package org.br.dietamais.data.repositorio

import kotlinx.serialization.json.encodeToJsonElement
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import kotlin.uuid.Uuid

/** Suplementos, orientações e modelos de orientação na ficha da nutri (BE-75, BE-76, MOB-92). Escrita online (C-09). */
interface PortaPrescricoesNutri {
    suspend fun suplementos(pacienteId: Uuid): List<SuplementoResposta>
    suspend fun prescrever(pacienteId: Uuid, requisicao: SuplementoRequisicao): SuplementoResposta
    suspend fun atualizarSuplemento(id: Uuid, versao: Long, requisicao: AtualizarSuplementoRequisicao): SuplementoResposta
    suspend fun orientacoes(pacienteId: Uuid): List<OrientacaoResposta>
    suspend fun enviarOrientacao(pacienteId: Uuid, requisicao: OrientacaoRequisicao): OrientacaoResposta
    suspend fun atualizarOrientacao(id: Uuid, versao: Long, requisicao: AtualizarOrientacaoRequisicao): OrientacaoResposta
    suspend fun modelosOrientacao(): List<ModeloOrientacaoResposta>
    suspend fun criarModeloOrientacao(requisicao: ModeloOrientacaoRequisicao): ModeloOrientacaoResposta
    suspend fun excluirModeloOrientacao(id: Uuid, versao: Long)
}

class PrescricoesNutriRepositorio(private val api: ApiDieta) : PortaPrescricoesNutri {
    override suspend fun suplementos(pacienteId: Uuid) = naSessao { api.obter<SuplementosResposta>("pacientes/$pacienteId/suplementos").itens }
    override suspend fun prescrever(pacienteId: Uuid, requisicao: SuplementoRequisicao) =
        naSessao { api.enviar<SuplementoResposta>("pacientes/$pacienteId/suplementos", JsonContrato.encodeToJsonElement(requisicao)) }
    override suspend fun atualizarSuplemento(id: Uuid, versao: Long, requisicao: AtualizarSuplementoRequisicao) =
        naSessao { api.alterar<SuplementoResposta>("suplementos/$id", JsonContrato.encodeToJsonElement(requisicao), versao) }
    override suspend fun orientacoes(pacienteId: Uuid) = naSessao { api.obter<OrientacoesResposta>("pacientes/$pacienteId/orientacoes").itens }
    override suspend fun enviarOrientacao(pacienteId: Uuid, requisicao: OrientacaoRequisicao) =
        naSessao { api.enviar<OrientacaoResposta>("pacientes/$pacienteId/orientacoes", JsonContrato.encodeToJsonElement(requisicao)) }
    override suspend fun atualizarOrientacao(id: Uuid, versao: Long, requisicao: AtualizarOrientacaoRequisicao) =
        naSessao { api.alterar<OrientacaoResposta>("orientacoes/$id", JsonContrato.encodeToJsonElement(requisicao), versao) }
    override suspend fun modelosOrientacao() = naSessao { api.obter<ModelosOrientacaoResposta>(MODELOS).itens }
    override suspend fun criarModeloOrientacao(requisicao: ModeloOrientacaoRequisicao) =
        naSessao { api.enviar<ModeloOrientacaoResposta>(MODELOS, JsonContrato.encodeToJsonElement(requisicao)) }
    override suspend fun excluirModeloOrientacao(id: Uuid, versao: Long) = naSessao { api.excluirComVersao("$MODELOS/$id", versao) }

    private suspend fun <T> naSessao(bloco: suspend () -> T): T {
        val contexto = api.operacaoSessao.value?.takeIf { it.contexto.perfil == Perfil.NUTRICIONISTA } ?: throw SessaoAlterada()
        return bloco().also { if (api.operacaoSessao.value != contexto) throw SessaoAlterada() }
    }

    private companion object { const val MODELOS = "eu/nutri/modelos-orientacao" }
}
