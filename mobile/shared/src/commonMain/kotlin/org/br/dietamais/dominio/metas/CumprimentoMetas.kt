package org.br.dietamais.dominio.metas

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.br.dietamais.data.remoto.dto.FrequenciaMeta
import org.br.dietamais.data.remoto.dto.MetaResposta
import org.br.dietamais.data.remoto.dto.RegistroMetaResposta

/** Resultado de RN-MET-03; [percentual] com uma casa, meio para cima (como o backend). */
data class ResultadoCumprimento(val diasPrevistos: Int, val diasCumpridos: Int, val percentual: Double, val sequenciaAtual: Int, val melhorSequencia: Int)

private class Unidade(val previstos: Int, val cumpridos: Int, val cumprida: Boolean, val aberta: Boolean)

/**
 * RN-MET-03 (MOB-90): cumprimento de uma meta numa janela, igual ao `CalculoCumprimento` do backend e conferido pelos vetores de `metas.json`.
 * Janela inclusiva, cortada pelo início e pelo fim da meta e por [hoje]. Registros do mesmo dia somam e o dia cumpre com soma maior ou igual ao alvo.
 * SEMANAL: semanas de segunda a domingo, previstos = N por semana (no máximo os dias da semana dentro da janela). O dia (ou a semana) ainda
 * aberto e não cumprido não quebra a sequência atual.
 */
fun cumprimento(frequencia: FrequenciaMeta, alvo: Double, vezesPorSemana: Int?, inicioMeta: LocalDate?, fimMeta: LocalDate?,
    de: LocalDate, ate: LocalDate, hoje: LocalDate, somaPorDia: Map<LocalDate, Double>): ResultadoCumprimento {
    val primeiro = if (inicioMeta != null && inicioMeta > de) inicioMeta else de
    var ultimo = ate
    if (fimMeta != null && fimMeta < ultimo) ultimo = fimMeta
    if (hoje < ultimo) ultimo = hoje
    if (ultimo < primeiro) return ResultadoCumprimento(0, 0, 0.0, 0, 0)
    fun cumpre(d: LocalDate): Boolean = (somaPorDia[d] ?: return false) >= alvo
    val unidades = mutableListOf<Unidade>()
    if (frequencia == FrequenciaMeta.SEMANAL) {
        val n = vezesPorSemana ?: 1
        var segunda = segundaDe(primeiro)
        while (segunda <= ultimo) {
            val inicio = if (segunda < primeiro) primeiro else segunda
            val domingo = segunda.plus(DatePeriod(days = 6)); val fim = if (domingo > ultimo) ultimo else domingo
            val previstos = minOf(n, inicio.daysUntil(fim) + 1)
            var cumpridos = 0; var d = inicio
            while (d <= fim) { if (cumpre(d)) cumpridos++; d = d.plus(DatePeriod(days = 1)) }
            val validos = minOf(cumpridos, previstos)
            unidades += Unidade(previstos, validos, validos >= previstos, domingo >= hoje)
            segunda = segunda.plus(DatePeriod(days = 7))
        }
    } else {
        var d = primeiro
        while (d <= ultimo) { val ok = cumpre(d); unidades += Unidade(1, if (ok) 1 else 0, ok, d >= hoje); d = d.plus(DatePeriod(days = 1)) }
    }
    var previstos = 0; var cumpridos = 0; var melhor = 0; var corrida = 0
    for (u in unidades) {
        previstos += u.previstos; cumpridos += u.cumpridos
        if (u.cumprida) { corrida++; melhor = maxOf(melhor, corrida) } else corrida = 0
    }
    var atual = 0; var i = unidades.lastIndex
    if (i >= 0 && !unidades[i].cumprida && unidades[i].aberta) i--
    while (i >= 0 && unidades[i].cumprida) { atual++; i-- }
    return ResultadoCumprimento(previstos, cumpridos, percentual(cumpridos, previstos), atual, melhor)
}

/** cumpridos × 100 / previstos com uma casa, meio para cima, em inteiros (sem erro de ponto flutuante). */
internal fun percentual(cumpridos: Int, previstos: Int): Double {
    if (previstos == 0) return 0.0
    val num = cumpridos * 1000L
    val decimos = num / previstos + (if (2 * (num % previstos) >= previstos) 1 else 0)
    return decimos / 10.0
}

fun segundaDe(dia: LocalDate): LocalDate = dia.minus(DatePeriod(days = dia.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal))

fun somaPorDia(registros: List<RegistroMetaResposta>): Map<LocalDate, Double> =
    registros.groupBy { it.data }.mapValues { (_, r) -> r.sumOf { it.valor } }

/** A meta vale no dia (dentro do início e do fim). */
fun metaValeEm(meta: MetaResposta, dia: LocalDate) = (meta.inicio == null || meta.inicio <= dia) && (meta.fim == null || meta.fim >= dia)

/** Situação de um dia na semana da meta: cumprido, não cumprido (dia que já passou), hoje ainda aberto, futuro ou fora do período. */
enum class SituacaoDia { CUMPRIDO, NAO_CUMPRIDO, ABERTO, FUTURO, FORA }

/** Visão da meta no Hoje e na semana (artboards `P-Hoje-Metas` e `P-Metas-Semana`). */
data class ProgressoMeta(val meta: MetaResposta, val hoje: Double, val semana: ResultadoCumprimento, val sequencia: Int,
    val dias: List<Pair<LocalDate, SituacaoDia>>) {
    val cumpridaHoje get() = hoje >= meta.alvo
    /** Semanal: quantas vezes ainda faltam na semana. */
    val faltamNaSemana get() = if (meta.frequencia == FrequenciaMeta.SEMANAL) maxOf(0, (meta.vezesPorSemana ?: 1) - semana.diasCumpridos) else 0
}

/**
 * Progresso de uma meta em [hoje]: soma do dia, cumprimento da semana (segunda a domingo) e sequência atual desde o início da meta
 * (ou do primeiro registro). A sequência é em dias na meta diária e em semanas na semanal.
 */
fun progressoMeta(meta: MetaResposta, registros: List<RegistroMetaResposta>, hoje: LocalDate): ProgressoMeta {
    val daMeta = registros.filter { it.metaId == meta.id }
    val soma = somaPorDia(daMeta)
    val segunda = segundaDe(hoje); val domingo = segunda.plus(DatePeriod(days = 6))
    val semana = cumprimento(meta.frequencia, meta.alvo, meta.vezesPorSemana, meta.inicio, meta.fim, segunda, domingo, hoje, soma)
    val desde = listOfNotNull(meta.inicio, soma.keys.minOrNull()).minOrNull() ?: hoje
    val sequencia = cumprimento(meta.frequencia, meta.alvo, meta.vezesPorSemana, meta.inicio, meta.fim, minOf(desde, segunda), hoje, hoje, soma).sequenciaAtual
    val dias = (0 until 7).map { i ->
        val d = segunda.plus(DatePeriod(days = i))
        d to when {
            !metaValeEm(meta, d) -> SituacaoDia.FORA
            (soma[d] ?: 0.0) >= meta.alvo -> SituacaoDia.CUMPRIDO
            d > hoje -> SituacaoDia.FUTURO
            d == hoje -> SituacaoDia.ABERTO
            else -> SituacaoDia.NAO_CUMPRIDO
        }
    }
    return ProgressoMeta(meta, soma[hoje] ?: 0.0, semana, sequencia, dias)
}
