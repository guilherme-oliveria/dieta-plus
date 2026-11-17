package org.br.dietamais.data.remoto.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable(with = TipoDocumentoSerializer::class)
enum class TipoDocumento { ATESTADO, DECLARACAO_COMPARECIMENTO, OUTRO }
object TipoDocumentoSerializer : EnumComOutro<TipoDocumento>("TipoDocumento", TipoDocumento.entries.toTypedArray(), TipoDocumento.OUTRO)

@Serializable(with = SituacaoDocumentoSerializer::class)
enum class SituacaoDocumento { VALIDO, ANULADO, OUTRO }
object SituacaoDocumentoSerializer : EnumComOutro<SituacaoDocumento>("SituacaoDocumento", SituacaoDocumento.entries.toTypedArray(), SituacaoDocumento.OUTRO)

/** Modelo de documento (BE-78, RN-DOC-01): os do sistema (`doSistema`) são só leitura; a nutri copia para editar. */
@Serializable
data class ModeloDocumentoResposta(val id: Uuid, val tipo: TipoDocumento, val nome: String, val texto: String, val doSistema: Boolean,
    val versao: Long, val criadoEm: Instant, val atualizadoEm: Instant)
@Serializable
data class ModelosDocumentoResposta(val itens: List<ModeloDocumentoResposta> = emptyList())
@Serializable
data class ModeloDocumentoRequisicao(val tipo: TipoDocumento, val nome: String, val texto: String)

/** Emissão (online): a declaração de comparecimento parte de uma consulta `REALIZADA`. O texto final é imutável (RN-DOC-02). */
@Serializable
data class EmitirDocumentoRequisicao(val id: Uuid? = null, val modeloId: Uuid, val consultaId: Uuid? = null, val data: LocalDate? = null,
    val horario: String? = null, val texto: String? = null)
@Serializable
data class DocumentoResposta(val id: Uuid, val pacienteId: Uuid, val tipo: TipoDocumento, val titulo: String, val texto: String,
    val codigoConferencia: String, val emitidoEm: Instant, val anuladoEm: Instant? = null, val motivoAnulacao: String? = null)
@Serializable
data class DocumentosResposta(val itens: List<DocumentoResposta> = emptyList())
@Serializable
data class AnularDocumentoRequisicao(val motivo: String)

/** O que o paciente recebe (BE-79): só metadados; o PDF vem por link assinado. Coleção `documentos` do sync. */
@Serializable
data class DocumentoPacienteResposta(val id: Uuid, val tipo: TipoDocumento, val titulo: String, val emitidoEm: Instant, val anulado: Boolean, val versao: Long)
@Serializable
data class DocumentosPacienteResposta(val itens: List<DocumentoPacienteResposta> = emptyList())

/** Conferência pública pelo código (RN-DOC-03): sem nenhum dado do paciente. */
@Serializable
data class ConferenciaDocumentoResposta(val tipo: TipoDocumento, val emissor: String, val crn: String? = null, val emitidoEm: Instant, val situacao: SituacaoDocumento)
