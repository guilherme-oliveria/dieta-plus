package org.br.dietamais.data.remoto.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** [limitePacientes] nulo = sem limite (BE-80). [precoAnual] nulo = o plano não é vendido por ano. */
@Serializable
data class PlanoResposta(val codigo: String, val nome: String, val precoMensal: Double = 0.0, val limitePacientes: Int? = null, val precoAnual: Double? = null)

/** Troca para um plano menor que só vale no próximo vencimento (RN-ASS-10, BE-81b). */
@Serializable
data class TrocaPlanoAgendadaDto(val planoCodigo: String, val periodicidade: Periodicidade = Periodicidade.MENSAL, val aPartirDe: LocalDate)

/**
 * `GET /assinatura`. Os campos da 1.4 são opcionais (BE-C10): [exibePrecoEBotao] é a chave por plataforma (D-09) e, ausente, vale `true`
 * (servidor da 1.3, que não tinha a chave).
 */
@Serializable
data class AssinaturaResposta(val organizacaoId: Uuid, val plano: PlanoResposta, val status: StatusAssinatura, val pacientesOcupados: Int,
    val fimPeriodoEm: Instant? = null, val carenciaAte: Instant? = null, val pagamentoDisponivel: Boolean = false,
    val planosDisponiveis: List<PlanoResposta> = emptyList(), val periodicidade: Periodicidade? = null, val diasRestantesTeste: Int? = null,
    val exibePrecoEBotao: Boolean? = null, val trocaAgendada: TrocaPlanoAgendadaDto? = null)

@Serializable
data class CheckoutRequisicao(val planoCodigo: String, val periodicidade: Periodicidade = Periodicidade.MENSAL)

/** Subir de plano devolve [url]; descer com a assinatura ativa só agenda a troca e devolve [trocaAgendadaPara] (RN-ASS-10). */
@Serializable
data class CheckoutResposta(val url: String? = null, val trocaAgendadaPara: LocalDate? = null) {
    override fun toString() = "CheckoutResposta[protegido]"
}
