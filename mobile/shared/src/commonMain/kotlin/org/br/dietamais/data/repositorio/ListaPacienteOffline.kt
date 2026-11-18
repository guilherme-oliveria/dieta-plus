package org.br.dietamais.data.repositorio

import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.br.dietamais.data.local.BancoDieta
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.sync.EscritaOffline
import kotlin.uuid.Uuid

/**
 * Lista de substituição do paciente offline-first (MOB-29c). Salvar grava a lista otimista no banco local e a mutação `listas` (só
 * `ATUALIZAR`, id = id da ficha) na fila, na mesma transação; o servidor decide no /sync. Copiar a lista de outro paciente continua online.
 *
 * - O pull do /sync guarda a lista pelo id do vínculo; a edição local fica pelo id da ficha, que é o da mutação. A leitura junta as duas.
 * - Uma mutação aberta por lista: o pedido é a diferença completa em relação à lista base da nutri, então a edição nova substitui a anterior
 *   (mesma `versaoBase`). Com conflito aberto, a decisão nas pendências vem antes.
 */
class ListaPacienteOffline(private val online: PortaListaPaciente, private val banco: BancoDieta, private val aoAlterar: () -> Unit = {},
    private val escrita: EscritaOffline = EscritaOffline(banco, aoEnfileirar = aoAlterar)) : PortaListaPaciente {
    override val escritaOffline: Boolean get() = true

    override suspend fun obter(pacienteId: Uuid): ListaSubstituicaoResposta {
        val sessao = sessao()
        val servidor = try { online.obter(pacienteId) } catch (falha: FalhaComunicacao) { return local(sessao.usuario_id, sessao.perfil, pacienteId) ?: throw falha }
        // o ajuste ainda não aceito vale mais que o estado do servidor
        if (aberta(sessao.usuario_id, sessao.perfil, pacienteId)) return local(sessao.usuario_id, sessao.perfil, pacienteId) ?: servidor
        guardar(sessao.usuario_id, sessao.perfil, servidor)
        return servidor
    }

    override suspend fun salvar(pacienteId: Uuid, versao: Long, requisicao: AtualizarListaSubstituicaoRequisicao): ListaSubstituicaoResposta {
        val sessao = sessao()
        val usuario = sessao.usuario_id; val perfil = sessao.perfil
        val atual = local(usuario, perfil, pacienteId) ?: throw ErroApi("RECURSO_NAO_ENCONTRADO", null, emptyList(), 404, null, null)
        val mutacoes = banco.mutacaoPendenteQueries.doRegistro(usuario, perfil, COLECAO, pacienteId.toString()).executeAsList()
        if (mutacoes.any { it.status == "CONFLITO" } || atual.versao != versao) throw conflito()
        val pedido = AtualizarListaSubstituicaoRequisicao(requisicao.adicionados.distinct(), requisicao.removidos.distinct())
        val dados = JsonContrato.encodeToJsonElement(pedido).jsonObject
        val aberta = mutacoes.lastOrNull { it.status == "PENDENTE" }
        if (aberta != null) {
            val nova = otimista(atual, pedido, atual.versao)
            banco.transaction {
                banco.mutacaoPendenteQueries.atualizarDados(dados.toString(), aberta.id_mutacao, usuario, perfil)
                banco.registroSyncQueries.salvar(usuario, perfil, COLECAO, pacienteId.toString(), 0, 0, JsonContrato.encodeToString(nova))
            }
            aoAlterar()
            return nova
        }
        // o instantâneo para desfazer uma rejeição é tirado do registro pelo id da ficha: garante que ele exista
        if (banco.registroSyncQueries.dadosDe(usuario, perfil, COLECAO, pacienteId.toString()).executeAsOneOrNull() == null)
            banco.registroSyncQueries.salvar(usuario, perfil, COLECAO, pacienteId.toString(), atual.versao, 0, JsonContrato.encodeToString(atual))
        val nova = otimista(atual, pedido, versao + 1)
        escrita.atualizar(COLECAO, pacienteId, versao, dados, JsonContrato.encodeToString(nova))
        return nova
    }

    /** Copiar troca a lista inteira no servidor: com ajuste ainda na fila, ele passaria por cima da cópia. */
    override suspend fun copiarDe(pacienteId: Uuid, origemId: Uuid, versao: Long): ListaSubstituicaoResposta {
        val sessao = sessao()
        if (aberta(sessao.usuario_id, sessao.perfil, pacienteId)) throw conflito()
        return online.copiarDe(pacienteId, origemId, versao).also { guardar(sessao.usuario_id, sessao.perfil, it) }
    }

    override fun pendenteDeEnvio(pacienteId: Uuid): Boolean {
        val sessao = banco.sessaoQueries.atual().executeAsOneOrNull() ?: return false
        return aberta(sessao.usuario_id, sessao.perfil, pacienteId)
    }

    // ---- internos
    private fun sessao() = banco.sessaoQueries.atual().executeAsOneOrNull()?.takeIf { it.perfil == Perfil.NUTRICIONISTA.name } ?: throw SessaoAlterada()

    private fun aberta(usuario: String, perfil: String, pacienteId: Uuid) =
        banco.mutacaoPendenteQueries.abertaDoRegistro(usuario, perfil, COLECAO, pacienteId.toString()).executeAsOne() > 0

    /** Com ajuste aberto vale o registro pela ficha; sem ele, a versão mais nova entre o pull (pelo vínculo) e o que a API devolveu. */
    private fun local(usuario: String, perfil: String, pacienteId: Uuid): ListaSubstituicaoResposta? {
        val propria = banco.registroSyncQueries.dadosDe(usuario, perfil, COLECAO, pacienteId.toString()).executeAsOneOrNull()?.dados_json?.let(::ler)
        if (propria != null && aberta(usuario, perfil, pacienteId)) return propria
        val todas = banco.registroSyncQueries.daSessao(COLECAO).executeAsList().mapNotNull(::ler).filter { it.pacienteId == pacienteId }
        val maior = todas.maxOfOrNull { it.versao } ?: return propria
        // na mesma versão, a do servidor (pull) vale mais que a prévia que ficou do envio
        return todas.filter { it.versao == maior }.firstOrNull { it != propria } ?: propria
    }

    private fun ler(json: String) = try { JsonContrato.decodeFromString<ListaSubstituicaoResposta>(json) } catch (_: Exception) { null }

    private fun guardar(usuario: String, perfil: String, lista: ListaSubstituicaoResposta) = banco.transaction {
        if (aberta(usuario, perfil, lista.pacienteId)) return@transaction
        val revisao = banco.registroSyncQueries.revisao(usuario, perfil, COLECAO, lista.pacienteId.toString()).executeAsOneOrNull()
        if (revisao != null && revisao > lista.versao) return@transaction
        banco.registroSyncQueries.salvar(usuario, perfil, COLECAO, lista.pacienteId.toString(), lista.versao, 0, JsonContrato.encodeToString(lista))
    }

    /**
     * Prévia da lista efetiva: a base da nutri é a efetiva sem os acréscimos e com as remoções de antes; a nova é essa base com o pedido.
     * Os dados de cada alimento vêm da lista atual ou do catálogo do aparelho; o servidor devolve a lista oficial no próximo pull.
     */
    private fun otimista(atual: ListaSubstituicaoResposta, pedido: AtualizarListaSubstituicaoRequisicao, versao: Long): ListaSubstituicaoResposta {
        val conhecidos = (catalogo() + atual.alimentos).associateBy { it.id }
        val base = atual.alimentos.map { it.id }.toSet() - atual.adicionados.toSet() + atual.removidos
        val efetiva = base - pedido.removidos.toSet() + pedido.adicionados
        return atual.copy(versao = versao, alimentos = efetiva.mapNotNull { conhecidos[it] }.sortedBy { it.nome.lowercase() },
            adicionados = pedido.adicionados, removidos = pedido.removidos)
    }

    private fun catalogo(): List<AlimentoResposta> = banco.alimentoCacheQueries.daSessao("alimentos").executeAsList()
        .mapNotNull { try { JsonContrato.decodeFromString<AlimentoResposta>(it) } catch (_: Exception) { null } }

    private fun conflito() = ErroApi("CONFLITO_VERSAO", "RN-SYN-02", emptyList(), 412, null, null)

    private companion object { const val COLECAO = "listas" }
}
