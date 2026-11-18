package org.br.dietamais.data.remoto.dto

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable(with = TipoRestricaoSaudeSerializer::class)
enum class TipoRestricaoSaude { ALERGIA, INTOLERANCIA, CONDICAO, MEDICAMENTO, OUTRO }
object TipoRestricaoSaudeSerializer : EnumComOutro<TipoRestricaoSaude>("TipoRestricaoSaude", TipoRestricaoSaude.entries.toTypedArray(), TipoRestricaoSaude.OUTRO)

@Serializable(with = GravidadeRestricaoSerializer::class)
enum class GravidadeRestricao { LEVE, MODERADA, GRAVE, OUTRO }
object GravidadeRestricaoSerializer : EnumComOutro<GravidadeRestricao>("GravidadeRestricao", GravidadeRestricao.entries.toTypedArray(), GravidadeRestricao.OUTRO)

/**
 * Item de saúde da ficha (BE-C9/BE-68, RN-SAU-01 e 02): alergia, intolerância, condição ou medicamento. Só a nutri vê.
 * `marcadores` liga o item aos marcadores de alimento (`AMENDOIM`, `CONTEM_LACTOSE`...); condição e medicamento não têm marcador.
 * `toString` não mostra os dados (dado de saúde).
 */
@Serializable
data class RestricaoSaudeDto(val id: Uuid, val tipo: TipoRestricaoSaude, val nome: String, val gravidade: GravidadeRestricao? = null,
    val observacao: String? = null, val marcadores: List<String> = emptyList()) {
    override fun toString() = "RestricaoSaudeDto(id=$id, tipo=$tipo)"
}

/** Alerta calculado pelo servidor num item da dieta (BE-70, RN-DIE-14). `tipo` é `ALERGIA` ou `INTOLERANCIA`; alergia exige confirmação para publicar. */
@Serializable
data class AlertaRestricaoDto(val restricaoId: Uuid, val tipo: TipoRestricaoSaude, val nome: String, val marcador: String, val exigeConfirmacao: Boolean)
