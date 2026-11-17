package org.br.dietamais.data.remoto.dto

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable(with = TipoConsultaSerializer::class)
enum class TipoConsulta { PRIMEIRA, RETORNO, ONLINE, AVALIACAO, OUTRO }
object TipoConsultaSerializer : EnumComOutro<TipoConsulta>("TipoConsulta", TipoConsulta.entries.toTypedArray(), TipoConsulta.OUTRO)

@Serializable(with = StatusConsultaSerializer::class)
enum class StatusConsulta { AGENDADA, REALIZADA, FALTA, CANCELADA, ENCERRADA, OUTRO }
object StatusConsultaSerializer : EnumComOutro<StatusConsulta>("StatusConsulta", StatusConsulta.entries.toTypedArray(), StatusConsulta.OUTRO)

/**
 * `pacienteId` é o id da ficha. `lugar`: endereço ou link da videochamada.
 * 1.4.0 (BE-C9, BE-71, RN-AGE-01/03/04): `modalidade`, `linkOnline` (só `https`), `orientacaoPaciente` e `observacaoInterna` (só a nutri recebe);
 * o paciente vê `ENCERRADA` no lugar de `REALIZADA` e `FALTA`. Tudo opcional: o backend 1.3.x não manda.
 */
@Serializable
data class ConsultaResposta(val id: Uuid, val pacienteId: Uuid, val vinculoId: Uuid, val inicio: Instant, val duracaoMin: Int, val tipo: TipoConsulta,
    val lugar: String? = null, val status: StatusConsulta, val versao: Long, val criadoEm: Instant, val atualizadoEm: Instant,
    val modalidade: ModalidadeConsulta? = null, val linkOnline: String? = null, val orientacaoPaciente: String? = null, val observacaoInterna: String? = null) {
    val fim: Instant get() = inicio + kotlin.time.Duration.parse("${duracaoMin}m")
}

@Serializable
data class ConsultasResposta(val itens: List<ConsultaResposta> = emptyList())

@Serializable
data class CriarConsultaRequisicao(val id: Uuid? = null, val pacienteId: Uuid, val inicio: Instant, val duracaoMin: Int, val tipo: TipoConsulta, val lugar: String? = null,
    val modalidade: ModalidadeConsulta? = null, val linkOnline: String? = null, val orientacaoPaciente: String? = null, val observacaoInterna: String? = null)

/** Campo nulo = não muda (o JSON do app omite nulos). `lugar` vazio limpa. */
@Serializable
data class AtualizarConsultaRequisicao(val inicio: Instant? = null, val duracaoMin: Int? = null, val tipo: TipoConsulta? = null, val lugar: String? = null, val status: StatusConsulta? = null,
    val modalidade: ModalidadeConsulta? = null, val linkOnline: String? = null, val orientacaoPaciente: String? = null, val observacaoInterna: String? = null)

@Serializable(with = ModalidadeConsultaSerializer::class)
enum class ModalidadeConsulta { PRESENCIAL, ONLINE, OUTRO }
object ModalidadeConsultaSerializer : EnumComOutro<ModalidadeConsulta>("ModalidadeConsulta", ModalidadeConsulta.entries.toTypedArray(), ModalidadeConsulta.OUTRO)

@Serializable(with = AntecedenciaLembreteSerializer::class)
enum class AntecedenciaLembrete { DUAS_HORAS, UM_DIA, DOIS_DIAS, OUTRO }
object AntecedenciaLembreteSerializer : EnumComOutro<AntecedenciaLembrete>("AntecedenciaLembrete", AntecedenciaLembrete.entries.toTypedArray(), AntecedenciaLembrete.OUTRO)

/** Preferências de lembrete da agenda da nutri (BE-72, `GET/PUT /eu/nutri/preferencias-agenda`, sem `ETag`). */
@Serializable
data class PreferenciasAgendaDto(val antecedencia: AntecedenciaLembrete, val lembreteExtraUmaHora: Boolean)
