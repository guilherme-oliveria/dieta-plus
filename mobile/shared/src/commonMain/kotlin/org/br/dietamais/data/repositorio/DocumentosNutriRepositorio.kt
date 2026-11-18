package org.br.dietamais.data.repositorio

import kotlinx.serialization.json.encodeToJsonElement
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import kotlin.uuid.Uuid

/** MOB-94 (BE-78): documentos e modelos de documento da nutri. Tudo exige rede e fica fora do sync da nutri. */
interface PortaDocumentosNutri {
    suspend fun modelos(): List<ModeloDocumentoResposta>
    suspend fun criarModelo(requisicao: ModeloDocumentoRequisicao): ModeloDocumentoResposta
    suspend fun atualizarModelo(id: Uuid, versao: Long, requisicao: ModeloDocumentoRequisicao): ModeloDocumentoResposta
    suspend fun excluirModelo(id: Uuid, versao: Long)
    suspend fun documentos(pacienteId: Uuid): List<DocumentoResposta>
    suspend fun emitir(pacienteId: Uuid, requisicao: EmitirDocumentoRequisicao): DocumentoResposta
    suspend fun anular(id: Uuid, motivo: String): DocumentoResposta
}

class DocumentosNutriRepositorio(private val api: ApiDieta) : PortaDocumentosNutri {
    private suspend fun <T> comNutri(bloco: suspend () -> T): T {
        val contexto = api.operacaoSessao.value?.takeIf { it.contexto.perfil == Perfil.NUTRICIONISTA } ?: throw SessaoAlterada()
        val r = bloco()
        if (api.operacaoSessao.value != contexto) throw SessaoAlterada()
        return r
    }

    override suspend fun modelos() = comNutri { api.obter<ModelosDocumentoResposta>("eu/nutri/modelos-documento").itens }
    override suspend fun criarModelo(requisicao: ModeloDocumentoRequisicao) =
        comNutri { api.enviar<ModeloDocumentoResposta>("eu/nutri/modelos-documento", JsonContrato.encodeToJsonElement(requisicao)) }
    override suspend fun atualizarModelo(id: Uuid, versao: Long, requisicao: ModeloDocumentoRequisicao) =
        comNutri { api.substituir<ModeloDocumentoResposta>("eu/nutri/modelos-documento/$id", JsonContrato.encodeToJsonElement(requisicao), versao) }
    override suspend fun excluirModelo(id: Uuid, versao: Long) = comNutri { api.excluirComVersao("eu/nutri/modelos-documento/$id", versao) }
    override suspend fun documentos(pacienteId: Uuid) = comNutri { api.obter<DocumentosResposta>("pacientes/$pacienteId/documentos").itens }
    override suspend fun emitir(pacienteId: Uuid, requisicao: EmitirDocumentoRequisicao) =
        comNutri { api.enviar<DocumentoResposta>("pacientes/$pacienteId/documentos", JsonContrato.encodeToJsonElement(requisicao), chave = requisicao.id ?: novoIdLocalDoc()) }
    override suspend fun anular(id: Uuid, motivo: String) =
        comNutri { api.enviar<DocumentoResposta>("documentos/$id/anular", JsonContrato.encodeToJsonElement(AnularDocumentoRequisicao(motivo.trim()))) }

    private fun novoIdLocalDoc() = org.br.dietamais.data.sync.novoIdLocal()
}
