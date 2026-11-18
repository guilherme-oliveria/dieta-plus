package org.br.dietamais.dominio.rotina

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.data.remoto.dto.JanelaAdesaoDto
import org.br.dietamais.data.remoto.dto.RegistroForaDoPlanoResposta
import kotlin.math.floor
import kotlin.time.Instant

/** MOB-89: nível da escala (1 a 5) mais próximo da média, para escolher o rótulo ("3,8" vira 4, "bastante"). Meio ponto arredonda para cima. */
fun nivelDaMedia(media: Double?): Int? = media?.takeIf { it.isFinite() }?.let { floor(it + 0.5).toInt().coerceIn(NIVEL_MIN, NIVEL_MAX) }

// Próprios desta tela: a escala do registro (MOB-88) pode entrar na develop depois.
private const val NIVEL_MIN = 1
private const val NIVEL_MAX = 5

/** Média com uma casa e vírgula: 3.75 vira "3,8". */
fun mediaUmaCasa(media: Double): String {
    val decimos = floor(media * 10 + 0.5).toLong()
    return "${decimos / 10},${decimos % 10}"
}

/** Refeições com registro (feita, trocada ou pulada) sobre as esperadas na janela. */
fun registradas(janela: JanelaAdesaoDto): Int = janela.feitas + janela.trocadas + janela.puladas

/** Primeiro e último dia da janela de [dias] terminando hoje, no fuso da nutri (o mesmo do backend). */
fun periodoDaJanela(dias: Int, agora: Instant, fuso: TimeZone): Pair<LocalDate, LocalDate> {
    val hoje = agora.toLocalDateTime(fuso).date
    return LocalDate.fromEpochDays(hoje.toEpochDays() - (dias - 1).coerceAtLeast(0)) to hoje
}

/** Registros fora do plano do mais novo ao mais antigo, com a hora local de cada um. */
fun foraDoPlanoOrdenado(registros: List<RegistroForaDoPlanoResposta>?, fuso: TimeZone): List<Pair<RegistroForaDoPlanoResposta, LocalDateTime>> =
    registros.orEmpty().sortedByDescending { it.registradoEm }.map { it to it.registradoEm.toLocalDateTime(fuso) }
