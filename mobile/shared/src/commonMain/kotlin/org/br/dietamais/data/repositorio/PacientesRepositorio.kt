package org.br.dietamais.data.repositorio

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.br.dietamais.data.local.BancoDieta
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.sync.*
import kotlin.uuid.Uuid

interface PortaPacientes {
    fun observar(): Flow<List<PacienteResposta>>
    suspend fun atualizar()
    suspend fun criar(requisicao: CriarPacienteRequisicao, chave: Uuid): PacienteResposta
    suspend fun convidar(id: Uuid, chave: Uuid): ConviteCriadoResposta
    suspend fun assinatura(): AssinaturaResumoResposta?
    /** BE-66: próxima consulta e última avaliação por vínculo; só online. Nulo quando não deu para ler (a lista segue sem os filtros de atenção). */
    suspend fun resumos(): Map<Uuid, org.br.dietamais.dominio.pacientes.ResumoPaciente>? = null
    /**
     * MOB-82 (RN-SAU-01): grava a lista inteira de itens de saúde junto com a ficha; a lista substitui a anterior.
     * O PATCH da ficha regrava todos os campos, então os outros dados vão como estão.
     */
    suspend fun salvarRestricoes(paciente: PacienteResposta, restricoes: List<RestricaoSaudeDto>): PacienteResposta = throw UnsupportedOperationException()
}

/** Requisição do PATCH da ficha que só troca as restrições: o resto vai como está (o servidor regrava os campos simples). */
fun requisicaoRestricoes(p: PacienteResposta, restricoes: List<RestricaoSaudeDto>) = AtualizarPacienteRequisicao(p.nome, p.email, p.dataNascimento, p.sexo, p.telefone,
    p.estaturaReferencia, p.objetivo, p.observacoes, cadastroAmpliado = null, restricoesSaude = restricoes)

/** Lê `GET /pacientes` (100 por página) e guarda só o resumo de cada vínculo. */
suspend fun carregarResumosPacientes(api: ApiDieta): Map<Uuid, org.br.dietamais.dominio.pacientes.ResumoPaciente>? {
    val esperado = api.operacaoSessao.value?.takeIf { it.contexto.perfil == Perfil.NUTRICIONISTA } ?: return null
    return try {
        val r = mutableMapOf<Uuid, org.br.dietamais.dominio.pacientes.ResumoPaciente>()
        var pagina = 0
        do {
            val resposta = api.obter<PaginaPacientesResposta>("pacientes", mapOf("pagina" to pagina.toString(), "tamanho" to "100", "ordem" to "nome,asc"))
            if (api.operacaoSessao.value != esperado || resposta.pagina != pagina || (resposta.itens.isEmpty() && r.size < resposta.total)) return null
            resposta.itens.forEach { r[it.vinculoId] = org.br.dietamais.dominio.pacientes.ResumoPaciente(it.proximaConsulta, it.ultimaAvaliacao?.dataHora, it.ultimaAvaliacao) }
            pagina++
        } while (r.size < resposta.total && pagina < 20)
        r
    } catch (cancelamento: kotlinx.coroutines.CancellationException) { throw cancelamento }
    catch (_: Exception) { null }
}

class PacientesRepositorio(private val api: ApiDieta, private val banco: BancoDieta,
    private val sincronizador: Sincronizador = criarSincronizador(api, banco), private val aoAlterar: () -> Unit = {},
    private val escrita: EscritaOffline = EscritaOffline(banco, aoEnfileirar = aoAlterar), private val agora: () -> kotlin.time.Instant = { kotlin.time.Clock.System.now() }) : PortaPacientes {
    private val operacoes = Mutex()
    override suspend fun resumos() = carregarResumosPacientes(api)
    override suspend fun assinatura(): AssinaturaResumoResposta? {
        val contexto = contexto()
        val eu = api.obter<EuResposta>("eu")
        conferir(contexto)
        if (eu.usuario.id != contexto.usuarioId || eu.usuario.perfil != contexto.perfil) throw RespostaInvalida()
        return eu.assinatura
    }
    override fun observar(): Flow<List<PacienteResposta>> = banco.pacienteCacheQueries.daSessao().asFlow()
        .mapToList(Dispatchers.Default).map { registros ->
            try { registros.map { JsonContrato.decodeFromString<PacienteResposta>(it) }
                .sortedWith(compareBy<PacienteResposta> { it.nome.lowercase() }.thenBy { it.vinculoId.toString() }) }
            catch (_: Exception) { throw RespostaInvalida() }
        }

    override suspend fun atualizar() = operacoes.withLock {
        val contexto = contexto()
        val iniciado = sincronizador.puxar()
        val resultado = if (iniciado == ResultadoLeituraSync.EM_ANDAMENTO)
            sincronizador.estado.first { it.resultado != ResultadoLeituraSync.EM_ANDAMENTO }.resultado else iniciado
        when (resultado) {
            ResultadoLeituraSync.CONCLUIDO -> conferir(contexto)
            ResultadoLeituraSync.SEM_REDE -> throw FalhaComunicacao()
            ResultadoLeituraSync.SEM_SESSAO, ResultadoLeituraSync.SESSAO_ALTERADA, ResultadoLeituraSync.CANCELADO -> throw SessaoAlterada()
            else -> {
                val erro = sincronizador.estado.value
                if (erro.codigoErro != null) throw ErroApi(erro.codigoErro, null, emptyList(), erro.statusHttp ?: 500, null, null)
                throw RespostaInvalida()
            }
        }
    }

    /**
     * Offline-first (MOB-29): grava a ficha otimista e a mutação na fila. O `vinculoId` provisório é o id da ficha; o servidor traz o real no pull.
     * Limite de plano e e-mail repetido (RN-ASS-03, RN-PAC) são decididos no /sync e voltam como pendência recusada.
     */
    override suspend fun criar(requisicao: CriarPacienteRequisicao, chave: Uuid): PacienteResposta = operacoes.withLock {
        contexto()
        val agoraLocal = agora()
        val otimista = PacienteResposta(requisicao.id, requisicao.id, requisicao.nome, requisicao.email, requisicao.dataNascimento, requisicao.sexo, requisicao.telefone,
            requisicao.estaturaReferencia, requisicao.objetivo, requisicao.observacoes, StatusVinculo.CONVIDADO, 0, agoraLocal, agoraLocal, cadastroAmpliado = requisicao.cadastroAmpliado)
        escrita.criar("pacientes", requisicao.id, JsonContrato.encodeToJsonElement(requisicao).jsonObject, JsonContrato.encodeToString(otimista))
        otimista
    }

    /** Offline-first (MOB-29/MOB-82): ficha otimista com as restrições novas e a mutação ATUALIZAR na fila, a partir da versão lida. */
    override suspend fun salvarRestricoes(paciente: PacienteResposta, restricoes: List<RestricaoSaudeDto>): PacienteResposta = operacoes.withLock {
        contexto()
        val otimista = paciente.copy(restricoesSaude = restricoes, versao = paciente.versao + 1, atualizadoEm = agora())
        escrita.atualizar("pacientes", paciente.id, paciente.versao, JsonContrato.encodeToJsonElement(requisicaoRestricoes(paciente, restricoes)).jsonObject,
            JsonContrato.encodeToString(otimista))
        otimista
    }

    override suspend fun convidar(id: Uuid, chave: Uuid): ConviteCriadoResposta = operacoes.withLock {
        val contexto = contexto()
        val resposta = api.enviar<ConviteCriadoResposta>("pacientes/$id/convite", chave = chave)
        conferir(contexto)
        val link = try { Url(resposta.link) } catch (_: Exception) { throw RespostaInvalida() }
        val protocoloPermitido = link.protocol == URLProtocol.HTTPS || (link.protocol.name == "dietamais" && link.host == "convites")
        if (resposta.codigo.length != 8 || !protocoloPermitido || !link.user.isNullOrEmpty() || !link.password.isNullOrEmpty()) throw RespostaInvalida()
        resposta
    }

    private fun contexto(): ContextoSessao = api.sessao.value?.takeIf { it.perfil == Perfil.NUTRICIONISTA } ?: throw SessaoAlterada()
    private fun conferir(esperado: ContextoSessao) {
        val sessao = banco.sessaoQueries.atual().executeAsOneOrNull()
        if (api.sessao.value != esperado || sessao?.usuario_id != esperado.usuarioId.toString() || sessao.perfil != esperado.perfil.name) throw SessaoAlterada()
    }
    private fun salvar(contexto: ContextoSessao, paciente: PacienteResposta) {
        banco.pacienteCacheQueries.salvar(contexto.usuarioId.toString(), contexto.perfil.name, paciente.vinculoId.toString(), paciente.id.toString(), JsonContrato.encodeToString(paciente))
    }
}
