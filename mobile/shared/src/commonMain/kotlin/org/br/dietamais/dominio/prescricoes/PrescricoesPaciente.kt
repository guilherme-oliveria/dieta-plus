package org.br.dietamais.dominio.prescricoes

import kotlinx.datetime.*
import org.br.dietamais.data.remoto.dto.OrientacaoResposta
import org.br.dietamais.data.remoto.dto.SuplementoResposta
import org.br.dietamais.dominio.rotina.JanelaSilencio
import org.br.dietamais.dominio.rotina.LIMITE_LEMBRETES
import org.br.dietamais.dominio.rotina.Lembrete
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-93: o que o paciente abre a partir da aba Dieta (ou do push "nova orientação"). */
enum class SecaoPrescricaoPaciente { SUPLEMENTOS, ORIENTACOES }

/** Suplementos que valem em [hoje] (começaram e não terminaram), na ordem do primeiro horário e depois do nome. */
fun suplementosVigentes(lista: List<SuplementoResposta>, hoje: LocalDate): List<SuplementoResposta> =
    lista.filter { it.inicio <= hoje && (it.fim == null || it.fim >= hoje) }
        .sortedWith(compareBy<SuplementoResposta> { horariosDe(it).firstOrNull() ?: LocalTime(23, 59) }.thenBy { it.nome.lowercase() })

/** Horários válidos (HH:MM) do suplemento, em ordem; o que não for hora é ignorado (o backend já valida). */
fun horariosDe(s: SuplementoResposta): List<LocalTime> = s.horarios.mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }.distinct().sorted()

/** Próxima tomada a partir de [agora], olhando hoje e os próximos dias dentro do período; nulo sem horário ou depois do fim. */
fun proximaTomada(s: SuplementoResposta, agora: Instant, fuso: TimeZone, dias: Int = 2): Instant? {
    val horarios = horariosDe(s)
    if (horarios.isEmpty()) return null
    val hoje = agora.toLocalDateTime(fuso).date
    for (delta in 0..dias) {
        val dia = hoje.plus(DatePeriod(days = delta))
        if (dia < s.inicio || (s.fim != null && dia > s.fim)) continue
        horarios.map { LocalDateTime(dia, it).toInstant(fuso) }.firstOrNull { it > agora }?.let { return it }
    }
    return null
}

/**
 * Lembretes locais dos suplementos (MOB-93) nos próximos [dias] dias, nos horários prescritos, dentro do período e fora do silêncio
 * (RN-NOT-03). Suplemento em [desligados] não lembra. O texto é genérico (sem nome nem dose), montado pela camada de apresentação.
 * A frequência é texto livre: o lembrete toca todo dia nos horários e o paciente pode desligar.
 */
fun planejarLembretesSuplemento(suplementos: List<SuplementoResposta>, agora: Instant, fuso: TimeZone, silencio: JanelaSilencio = JanelaSilencio(),
    desligados: Set<Uuid> = emptySet(), dias: Int = 7): List<Lembrete> {
    val hoje = agora.toLocalDateTime(fuso).date
    return suplementos.filter { it.id !in desligados }.flatMap { s ->
        (0 until dias).flatMap { delta ->
            val dia = hoje.plus(DatePeriod(days = delta))
            if (dia < s.inicio || (s.fim != null && dia > s.fim)) emptyList()
            else horariosDe(s).mapNotNull { hora ->
                val quando = LocalDateTime(dia, hora).toInstant(fuso)
                if (quando <= agora || silencio.contem(hora)) null
                else Lembrete("sup-${s.id}-$dia-${hora.hour}${hora.minute.toString().padStart(2, '0')}", quando, s.id, "")
            }
        }
    }.sortedBy { it.quando }
}

/** Junta os lembretes de refeição e de suplemento respeitando o limite de notificações pendentes do iOS. */
fun juntarLembretes(refeicoes: List<Lembrete>, suplementos: List<Lembrete>, limite: Int = LIMITE_LEMBRETES): List<Lembrete> =
    (refeicoes + suplementos).sortedBy { it.quando }.take(limite)

/** Orientações que o paciente vê: as não arquivadas, da mais nova para a mais antiga. */
fun orientacoesVisiveis(lista: List<OrientacaoResposta>): List<OrientacaoResposta> =
    lista.filter { it.arquivadaEm == null }.sortedWith(compareByDescending<OrientacaoResposta> { it.criadoEm }.thenBy { it.id.toString() })

/** Trecho de uma linha da orientação: `**assim**` vira negrito. */
data class TrechoTexto(val texto: String, val negrito: Boolean = false)

sealed interface BlocoTexto {
    val trechos: List<TrechoTexto>
    data class Paragrafo(override val trechos: List<TrechoTexto>) : BlocoTexto
    data class ItemLista(override val trechos: List<TrechoTexto>) : BlocoTexto
}

/**
 * Lê o Markdown restrito da orientação (RN-ORI-01): só negrito (`**texto**`) e item de lista (`- ` ou `* ` no começo da linha).
 * Qualquer outra marcação aparece como texto. Linhas vazias separam parágrafos e somem.
 */
fun lerTextoOrientacao(texto: String): List<BlocoTexto> = texto.lines().mapNotNull { linha ->
    val limpa = linha.trimEnd()
    when {
        limpa.isBlank() -> null
        limpa.trimStart().let { it.startsWith("- ") || it.startsWith("* ") } -> BlocoTexto.ItemLista(trechos(limpa.trimStart().drop(2).trim()))
        else -> BlocoTexto.Paragrafo(trechos(limpa.trim()))
    }
}

private fun trechos(linha: String): List<TrechoTexto> {
    val partes = linha.split("**")
    // número par de "**" sem fechar: o último fica como texto
    val fechado = partes.size % 2 == 1
    val saida = mutableListOf<TrechoTexto>()
    partes.forEachIndexed { i, parte ->
        val negrito = i % 2 == 1 && (fechado || i < partes.size - 1)
        val texto = if (i % 2 == 1 && !negrito) "**$parte" else parte
        if (texto.isNotEmpty()) {
            val ultimo = saida.lastOrNull()
            if (ultimo != null && ultimo.negrito == negrito) saida[saida.size - 1] = ultimo.copy(texto = ultimo.texto + texto)
            else saida += TrechoTexto(texto, negrito)
        }
    }
    return saida
}
