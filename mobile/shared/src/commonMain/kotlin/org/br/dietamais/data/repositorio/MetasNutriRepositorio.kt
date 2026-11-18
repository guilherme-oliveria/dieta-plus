package org.br.dietamais.data.repositorio

import kotlinx.serialization.json.encodeToJsonElement
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import kotlin.uuid.Uuid

/** Metas do paciente e modelos de metas na ficha da nutri (BE-74, BE-86, MOB-91). Escrita e leitura online (C-09). */
interface PortaMetasNutri {
    suspend fun metas(pacienteId: Uuid): List<MetaResposta>
    suspend fun cumprimento(pacienteId: Uuid): CumprimentoMetasResposta
    suspend fun criar(pacienteId: Uuid, requisicao: MetaRequisicao): MetaResposta
    suspend fun atualizar(metaId: Uuid, versao: Long, requisicao: AtualizarMetaRequisicao): MetaResposta
    suspend fun modelos(): List<ModeloMetaResposta>
    suspend fun criarModelo(requisicao: ModeloMetaRequisicao): ModeloMetaResposta
    suspend fun excluirModelo(id: Uuid, versao: Long)
    suspend fun aplicarModelo(pacienteId: Uuid, modeloId: Uuid): List<MetaResposta>
}

class MetasNutriRepositorio(private val api: ApiDieta) : PortaMetasNutri {
    override suspend fun metas(pacienteId: Uuid) = naSessao { api.obter<MetasResposta>("pacientes/$pacienteId/metas").itens }
    override suspend fun cumprimento(pacienteId: Uuid) = naSessao { api.obter<CumprimentoMetasResposta>("pacientes/$pacienteId/metas/cumprimento") }
    override suspend fun criar(pacienteId: Uuid, requisicao: MetaRequisicao) =
        naSessao { api.enviar<MetaResposta>("pacientes/$pacienteId/metas", JsonContrato.encodeToJsonElement(requisicao)) }
    override suspend fun atualizar(metaId: Uuid, versao: Long, requisicao: AtualizarMetaRequisicao) =
        naSessao { api.alterar<MetaResposta>("metas/$metaId", JsonContrato.encodeToJsonElement(requisicao), versao) }
    override suspend fun modelos() = naSessao { api.obter<ModelosMetaResposta>(MODELOS).itens }
    override suspend fun criarModelo(requisicao: ModeloMetaRequisicao) =
        naSessao { api.enviar<ModeloMetaResposta>(MODELOS, JsonContrato.encodeToJsonElement(requisicao)) }
    override suspend fun excluirModelo(id: Uuid, versao: Long) = naSessao { api.excluirComVersao("$MODELOS/$id", versao) }
    override suspend fun aplicarModelo(pacienteId: Uuid, modeloId: Uuid) =
        naSessao { api.enviar<MetasResposta>("pacientes/$pacienteId/metas/de-modelo/$modeloId").itens }

    /** A resposta só vale se a sessão da nutri for a mesma do começo da chamada. */
    private suspend fun <T> naSessao(bloco: suspend () -> T): T {
        val contexto = api.operacaoSessao.value?.takeIf { it.contexto.perfil == Perfil.NUTRICIONISTA } ?: throw SessaoAlterada()
        return bloco().also { if (api.operacaoSessao.value != contexto) throw SessaoAlterada() }
    }

    private companion object { const val MODELOS = "eu/nutri/modelos-meta" }
}
