package org.br.dietamais.data.repositorio

import kotlinx.serialization.json.encodeToJsonElement
import org.br.dietamais.data.local.BancoDieta
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.sync.novoIdLocal
import kotlin.uuid.Uuid

/** Dietas de um paciente na visão da nutri (MOB-12/13). Na web tudo passa pela API; no app o rascunho grava offline ([DietasOffline], MOB-29b). */
interface PortaDietas {
    suspend fun listar(pacienteId: Uuid): List<DietaResposta>
    suspend fun criar(pacienteId: Uuid, requisicao: SalvarDietaRequisicao, chave: Uuid): DietaResposta
    suspend fun salvar(id: Uuid, versao: Long, requisicao: SalvarDietaRequisicao): DietaResposta
    suspend fun publicar(id: Uuid, versao: Long): DietaSnapshotResposta
    suspend fun duplicar(id: Uuid, chave: Uuid = novoIdLocal()): DietaResposta
    /** [previa] só importa para a escrita offline: totais e nomes que a tela calculou, até o servidor devolver os oficiais. */
    suspend fun criar(pacienteId: Uuid, requisicao: SalvarDietaRequisicao, chave: Uuid, previa: PreviaDieta): DietaResposta = criar(pacienteId, requisicao, chave)
    suspend fun salvar(id: Uuid, versao: Long, requisicao: SalvarDietaRequisicao, previa: PreviaDieta): DietaResposta = salvar(id, versao, requisicao)
    /** true = salvar rascunho funciona sem rede (o envio fica na fila). */
    val escritaOffline: Boolean get() = false
    /** Dietas com edição local que o servidor ainda não confirmou (pendente ou em conflito). */
    fun pendentesDeEnvio(): Set<Uuid> = emptySet()
    /** Rascunhos criados no aparelho que o servidor ainda não recebeu (os únicos que dá para descartar). */
    fun rascunhosSoLocais(): Set<Uuid> = emptySet()
    /** Apaga o rascunho que nunca saiu do aparelho; false se ele já chegou ao servidor. */
    suspend fun descartarRascunhoLocal(id: Uuid): Boolean = false
}

/** Prévia local de uma dieta salva offline: totais do dia e nome de item que a requisição não carrega (produto da nutri). */
data class PreviaDieta(val totais: TotaisNutricionaisResposta? = null, val nomes: Map<Uuid, String> = emptyMap()) {
    override fun toString() = "PreviaDieta[conteúdo clínico protegido]"
}

/** [conferirArmazem] confere a sessão guardada fora da memória; a web (sem banco) não tem nada a conferir. */
class DietasRepositorio(private val api: ApiDieta, private val conferirArmazem: (OperacaoSessao) -> Unit) : PortaDietas {
    constructor(api: ApiDieta, banco: BancoDieta) : this(api, { esperado ->
        val sessao = banco.sessaoQueries.atual().executeAsOneOrNull()
        if (sessao?.usuario_id != esperado.contexto.usuarioId.toString() || sessao.perfil != esperado.contexto.perfil.name) throw SessaoAlterada()
    })

    override suspend fun listar(pacienteId: Uuid): List<DietaResposta> {
        val contexto = contexto()
        val resposta = api.obter<DietasResposta>("pacientes/$pacienteId/dietas")
        conferir(contexto)
        if (resposta.itens.any { it.pacienteId != pacienteId }) throw RespostaInvalida()
        return resposta.itens
    }
    override suspend fun criar(pacienteId: Uuid, requisicao: SalvarDietaRequisicao, chave: Uuid): DietaResposta {
        val contexto = contexto()
        val resposta = api.enviar<DietaResposta>("pacientes/$pacienteId/dietas", JsonContrato.encodeToJsonElement(requisicao), chave)
        conferir(contexto)
        if (resposta.pacienteId != pacienteId || (requisicao.id != null && resposta.id != requisicao.id)) throw RespostaInvalida()
        return resposta
    }
    override suspend fun salvar(id: Uuid, versao: Long, requisicao: SalvarDietaRequisicao): DietaResposta {
        val contexto = contexto()
        val resposta = api.substituir<DietaResposta>("dietas/$id", JsonContrato.encodeToJsonElement(requisicao), versao)
        conferir(contexto)
        if (resposta.id != id) throw RespostaInvalida()
        return resposta
    }
    override suspend fun publicar(id: Uuid, versao: Long): DietaSnapshotResposta {
        val contexto = contexto()
        val resposta = api.enviarComVersao<DietaSnapshotResposta>("dietas/$id/publicar", versao)
        conferir(contexto)
        if (resposta.dietaId != id) throw RespostaInvalida()
        return resposta
    }
    override suspend fun duplicar(id: Uuid, chave: Uuid): DietaResposta {
        val contexto = contexto()
        val resposta = api.enviar<DietaResposta>("dietas/$id/duplicar", chave = chave)
        conferir(contexto)
        return resposta
    }
    private fun contexto() = api.operacaoSessao.value?.takeIf { it.contexto.perfil == Perfil.NUTRICIONISTA } ?: throw SessaoAlterada()
    private fun conferir(esperado: OperacaoSessao) {
        if (api.operacaoSessao.value != esperado) throw SessaoAlterada()
        conferirArmazem(esperado)
    }
}
