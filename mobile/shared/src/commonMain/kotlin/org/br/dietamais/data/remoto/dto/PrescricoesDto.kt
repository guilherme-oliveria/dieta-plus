package org.br.dietamais.data.remoto.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Suplemento prescrito (BE-75, RN-SUP-01 e 02): produto da nutri (`produtoId`) ou texto livre (`nome`), com cópia congelada de nome e dose.
 * A nutri escreve online; nutri e paciente leem pelo sync (coleção `prescricoesSuplemento`). `horarios` em "HH:mm".
 */
@Serializable
data class SuplementoResposta(val id: Uuid, val pacienteId: Uuid, val produtoId: Uuid? = null, val nome: String, val dose: String,
    val forma: String? = null, val frequencia: String? = null, val horarios: List<String> = emptyList(), val inicio: LocalDate, val fim: LocalDate? = null,
    val versao: Long, val criadoEm: Instant, val atualizadoEm: Instant)
@Serializable
data class SuplementosResposta(val itens: List<SuplementoResposta> = emptyList())
@Serializable
data class SuplementoRequisicao(val id: Uuid? = null, val produtoId: Uuid? = null, val nome: String? = null, val dose: String,
    val forma: String? = null, val frequencia: String? = null, val horarios: List<String>? = null, val inicio: LocalDate? = null, val fim: LocalDate? = null)
@Serializable
data class AtualizarSuplementoRequisicao(val dose: String? = null, val forma: String? = null, val frequencia: String? = null,
    val horarios: List<String>? = null, val fim: LocalDate? = null, val encerrar: Boolean? = null)

/** Orientação avulsa (BE-76, RN-ORI-01): `texto` em Markdown restrito (nunca HTML). Arquivada some da lista do paciente. */
@Serializable
data class OrientacaoResposta(val id: Uuid, val pacienteId: Uuid, val titulo: String, val texto: String, val arquivadaEm: Instant? = null,
    val versao: Long, val criadoEm: Instant, val atualizadoEm: Instant)
@Serializable
data class OrientacoesResposta(val itens: List<OrientacaoResposta> = emptyList())
@Serializable
data class OrientacaoRequisicao(val id: Uuid? = null, val titulo: String, val texto: String)
@Serializable
data class AtualizarOrientacaoRequisicao(val titulo: String? = null, val texto: String? = null, val arquivar: Boolean? = null)
@Serializable
data class ModeloOrientacaoRequisicao(val titulo: String, val texto: String)
@Serializable
data class ModeloOrientacaoResposta(val id: Uuid, val titulo: String, val texto: String, val versao: Long, val criadoEm: Instant, val atualizadoEm: Instant)
@Serializable
data class ModelosOrientacaoResposta(val itens: List<ModeloOrientacaoResposta> = emptyList())
