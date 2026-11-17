package org.br.dietamais.data.remoto.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable(with = StatusCheckinSerializer::class)
enum class StatusCheckin { FEITO, TROCADO, PULADO, OUTRO }
object StatusCheckinSerializer : EnumComOutro<StatusCheckin>("StatusCheckin", StatusCheckin.entries.toTypedArray(), StatusCheckin.OUTRO)

@Serializable
data class TrocaCheckin(val itemId: Uuid, val alimentoId: Uuid)

@Serializable
data class CheckinResposta(val id: Uuid, val pacienteId: Uuid, val dietaId: Uuid, val refeicaoId: Uuid, val data: LocalDate,
    val status: StatusCheckin, val trocas: List<TrocaCheckin> = emptyList(), val registradoEm: Instant,
    /** BE-73 (RN-ROT-01): fome antes e sensação depois, de 1 a 5, opcionais. */
    val fomeAntes: Int? = null, val sensacaoDepois: Int? = null)
@Serializable
data class AguaResposta(val id: Uuid, val pacienteId: Uuid, val ml: Int, val registradoEm: Instant)
@Serializable
data class PesoResposta(val id: Uuid, val pacienteId: Uuid, val pesoKg: Double, val registradoEm: Instant)

@Serializable
data class CheckinRequisicao(val id: Uuid? = null, val dietaId: Uuid, val refeicaoId: Uuid, val data: LocalDate, val status: StatusCheckin,
    val trocas: List<TrocaCheckin> = emptyList(), val registradoEm: Instant, val fomeAntes: Int? = null, val sensacaoDepois: Int? = null)
@Serializable
data class AguaRequisicao(val id: Uuid? = null, val ml: Int, val registradoEm: Instant)
@Serializable
data class PesoRequisicao(val id: Uuid? = null, val pesoKg: Double, val registradoEm: Instant)

@Serializable
data class RotinaResposta(val checkins: List<CheckinResposta> = emptyList(), val agua: List<AguaResposta> = emptyList(), val pesos: List<PesoResposta> = emptyList(),
    val registrosForaDoPlano: List<RegistroForaDoPlanoResposta> = emptyList())

/** Registro fora do plano (BE-73, RN-ROT-06): só inclusão, pelo REST ou pela coleção `registrosForaDoPlano` do sync. */
@Serializable
data class RegistroForaDoPlanoRequisicao(val id: Uuid? = null, val data: LocalDate, val texto: String, val refeicaoId: Uuid? = null, val registradoEm: Instant)
@Serializable
data class RegistroForaDoPlanoResposta(val id: Uuid, val data: LocalDate, val texto: String, val refeicaoId: Uuid? = null, val registradoEm: Instant)

/** Médias da semana de fome e sensação (BE-73), em `GET /pacientes/{id}/adesao`. */
@Serializable
data class HumorAdesaoDto(val mediaFomeAntes: Double? = null, val mediaSensacaoDepois: Double? = null, val registros: Int)
