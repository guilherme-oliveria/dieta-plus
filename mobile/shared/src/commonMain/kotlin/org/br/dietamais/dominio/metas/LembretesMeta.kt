package org.br.dietamais.dominio.metas

import kotlinx.datetime.*
import org.br.dietamais.data.remoto.dto.FrequenciaMeta
import org.br.dietamais.data.remoto.dto.MetaResposta
import org.br.dietamais.data.remoto.dto.RegistroMetaResposta
import org.br.dietamais.data.remoto.dto.TipoMeta
import org.br.dietamais.dominio.rotina.JanelaSilencio
import org.br.dietamais.dominio.rotina.Lembrete
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-90b: até quatro horários de lembrete por meta. */
const val MAX_HORARIOS_META = 4

/** Horários sugeridos ao ligar o lembrete: a água ao longo do dia (como no artboard `P-Metas-Semana`), as outras à noite. */
fun horariosPadraoMeta(tipo: TipoMeta): List<LocalTime> =
    if (tipo == TipoMeta.AGUA) listOf(LocalTime(9, 0), LocalTime(13, 0), LocalTime(17, 0)) else listOf(LocalTime(20, 0))

/**
 * Lê "9:00, 13h, 17:30" (separados por vírgula, ponto e vírgula ou espaço). Aceita "9", "9h", "9:00" e "09h30".
 * Nulo se algum não for hora ou se ficar vazio ou com mais de [MAX_HORARIOS_META]; repetidos contam uma vez e saem em ordem.
 */
fun lerHorariosMeta(texto: String): List<LocalTime>? {
    val partes = texto.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    if (partes.isEmpty()) return null
    val horas = partes.map { lerHora(it) ?: return null }.distinct().sorted()
    return horas.takeIf { it.size <= MAX_HORARIOS_META }
}

private fun lerHora(texto: String): LocalTime? {
    val m = Regex("""^(\d{1,2})(?:[:hH](\d{2})?)?$""").matchEntire(texto) ?: return null
    val h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    return if (h in 0..23 && min in 0..59) LocalTime(h, min) else null
}

/** "9:00, 13:00, 17:30" para o campo de edição. */
fun textoHorariosMeta(horarios: List<LocalTime>): String = horarios.joinToString(", ") { formatarHora(it) }

fun formatarHora(h: LocalTime) = "${h.hour}:${h.minute.toString().padStart(2, '0')}"

/**
 * Lembretes locais das metas (MOB-90b) nos próximos [dias] dias: só as metas com lembrete ligado neste aparelho ([horarios]),
 * nos dias em que a meta vale (some quando ela acaba) e fora do horário de silêncio (RN-NOT-03 e RN-ROT-05).
 * Não lembra o que já foi cumprido: na meta diária, o dia com a soma no alvo; na semanal, o resto da semana já completa.
 * O texto é genérico, sem nome nem valor da meta, e é montado pela camada de apresentação.
 */
fun planejarLembretesMeta(metas: List<MetaResposta>, registros: List<RegistroMetaResposta>, horarios: Map<Uuid, List<LocalTime>>,
    agora: Instant, fuso: TimeZone, silencio: JanelaSilencio = JanelaSilencio(), dias: Int = 7): List<Lembrete> {
    val hoje = agora.toLocalDateTime(fuso).date
    return metas.filter { !horarios[it.id].isNullOrEmpty() }.flatMap { meta ->
        val soma = somaPorDia(registros.filter { it.metaId == meta.id })
        (0 until dias).flatMap { delta ->
            val dia = hoje.plus(DatePeriod(days = delta))
            if (!metaValeEm(meta, dia) || jaCumprida(meta, soma, dia)) emptyList()
            else horarios.getValue(meta.id).mapNotNull { hora ->
                val quando = LocalDateTime(dia, hora).toInstant(fuso)
                if (quando <= agora || silencio.contem(hora)) null
                else Lembrete("meta-${meta.id}-$dia-${hora.hour}${hora.minute.toString().padStart(2, '0')}", quando, meta.id, "")
            }
        }
    }.sortedBy { it.quando }
}

private fun jaCumprida(meta: MetaResposta, soma: Map<LocalDate, Double>, dia: LocalDate): Boolean {
    if (meta.frequencia == FrequenciaMeta.DIARIA) return (soma[dia] ?: 0.0) >= meta.alvo
    val segunda = segundaDe(dia)
    val cumpridos = (0..6).count { (soma[segunda.plus(DatePeriod(days = it))] ?: 0.0) >= meta.alvo }
    return cumpridos >= (meta.vezesPorSemana ?: 1)
}
