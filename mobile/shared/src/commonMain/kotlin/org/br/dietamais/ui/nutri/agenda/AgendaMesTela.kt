package org.br.dietamais.ui.nutri.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.dominio.agenda.*
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.CardDieta
import org.br.dietamais.ui.comum.icones.IconesDieta
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** Largura a partir da qual o calendário e a lista do dia ficam lado a lado (tablet deitado e desktop). */
private val LARGURA_LADO_A_LADO = 720.dp
private const val PONTOS_MAXIMOS = 3

/**
 * MOB-85 (artboard `A-Agenda-Mes`): calendário do mês com um ponto por consulta (até 3) e, abaixo ou ao lado, a lista do dia escolhido
 * com as consultas e os horários livres; tocar num horário livre abre a nova consulta com a data e a hora preenchidas.
 */
@Composable
internal fun AgendaMes(estado: EstadoAgenda, acoes: AcoesAgenda, online: Boolean, fuso: TimeZone, agora: Instant, cartao: @Composable (org.br.dietamais.data.remoto.dto.ConsultaResposta) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= LARGURA_LADO_A_LADO) Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
            Box(Modifier.weight(1f)) { CalendarioMes(estado, acoes, fuso, agora) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) { ListaDoDia(estado, acoes, online, fuso, agora, cartao) }
        } else Column(verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
            CalendarioMes(estado, acoes, fuso, agora)
            ListaDoDia(estado, acoes, online, fuso, agora, cartao)
        }
    }
}

@Composable
fun nomeMes(mes: Month): String = stringResource(when (mes) {
    Month.JANUARY -> Res.string.agenda_mes_1; Month.FEBRUARY -> Res.string.agenda_mes_2; Month.MARCH -> Res.string.agenda_mes_3
    Month.APRIL -> Res.string.agenda_mes_4; Month.MAY -> Res.string.agenda_mes_5; Month.JUNE -> Res.string.agenda_mes_6
    Month.JULY -> Res.string.agenda_mes_7; Month.AUGUST -> Res.string.agenda_mes_8; Month.SEPTEMBER -> Res.string.agenda_mes_9
    Month.OCTOBER -> Res.string.agenda_mes_10; Month.NOVEMBER -> Res.string.agenda_mes_11; Month.DECEMBER -> Res.string.agenda_mes_12
})

@Composable
fun rotuloMes(dia: LocalDate): String = stringResource(Res.string.agenda_mes_titulo, nomeMes(dia.month), dia.year)

@Composable
private fun CalendarioMes(estado: EstadoAgenda, acoes: AcoesAgenda, fuso: TimeZone, agora: Instant) {
    val hoje = agora.toLocalDateTime(fuso).date
    val contagem = consultasPorDiaDoMes(estado.consultas, estado.dia, fuso)
    val iniciais = stringResource(Res.string.agenda_mes_iniciais).split(',')
    CardDieta(Modifier.fillMaxWidth().testTag("calendario-mes")) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            iniciais.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        gradeDoMes(estado.dia).chunked(7).forEach { semana ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.espacoMinimo)) {
                semana.forEach { dia ->
                    Box(Modifier.weight(1f)) { if (dia != null) CelulaDia(dia, contagem[dia] ?: 0, dia == estado.dia, dia == hoje) { acoes.aoDia(dia) } }
                }
            }
        }
    }
}

@Composable
private fun CelulaDia(dia: LocalDate, consultas: Int, escolhido: Boolean, hoje: Boolean, aoTocar: () -> Unit) {
    val mes = nomeMes(dia.month).lowercase()
    val base = when (consultas) {
        0 -> stringResource(Res.string.agenda_mes_celula_livre, dia.day, mes)
        1 -> stringResource(Res.string.agenda_mes_celula_uma, dia.day, mes)
        else -> stringResource(Res.string.agenda_mes_celula_varias, dia.day, mes, consultas)
    }
    val descricao = if (hoje) stringResource(Res.string.agenda_mes_celula_hoje, base) else base
    val cores = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().height(Dimens.alvoToque).clip(forma)
        .background(if (escolhido || hoje) cores.primaryContainer else cores.surface)
        .border(if (escolhido) 2.dp else 1.dp, if (escolhido || hoje) cores.primary else cores.surface, forma)
        .clickable(onClick = aoTocar)
        .semantics(mergeDescendants = true) { contentDescription = descricao; role = Role.Button; selected = escolhido }
        .testTag("dia-mes-$dia"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(dia.day.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics { })
        Row(Modifier.height(6.dp).padding(top = 1.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(minOf(consultas, PONTOS_MAXIMOS)) { Box(Modifier.size(5.dp).clip(CircleShape).background(cores.primary)) }
        }
    }
}

@Composable
private fun ListaDoDia(estado: EstadoAgenda, acoes: AcoesAgenda, online: Boolean, fuso: TimeZone, agora: Instant, cartao: @Composable (org.br.dietamais.data.remoto.dto.ConsultaResposta) -> Unit) {
    val dia = estado.dia
    val curta = "${dia.day.toString().padStart(2, '0')}/${dia.month.number.toString().padStart(2, '0')}"
    val linhas = linhasDoDia(estado.consultas, dia, fuso, agora)
    val quantas = linhas.count { it is LinhaDoDia.Consulta && it.consulta.status != org.br.dietamais.data.remoto.dto.StatusConsulta.CANCELADA }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.agenda_mes_dia_titulo, nomeDiaSemana(dia.dayOfWeek), curta), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text(when (quantas) {
            0 -> stringResource(Res.string.agenda_mes_contagem_nenhuma)
            1 -> stringResource(Res.string.agenda_mes_contagem_uma)
            else -> stringResource(Res.string.agenda_mes_contagem_varias, quantas)
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val podeAgendar = online && estado.pacientesAgendaveis.isNotEmpty()
    linhas.forEach { linha ->
        when (linha) {
            is LinhaDoDia.Consulta -> cartao(linha.consulta)
            is LinhaDoDia.Livre -> HorarioLivre(linha.hora, nomeDiaSemana(dia.dayOfWeek) + " " + curta, podeAgendar) { acoes.aoNovaEm(dia, linha.hora) }
        }
    }
    if (linhas.none { it is LinhaDoDia.Livre }) Text(stringResource(Res.string.agenda_mes_sem_livre), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HorarioLivre(hora: Int, dia: String, habilitado: Boolean, aoTocar: () -> Unit) {
    val texto = hora.toString().padStart(2, '0') + ":00"
    val descricao = stringResource(Res.string.agenda_livre_descricao, dia, texto)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Text(texto, Modifier.width(52.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(aoTocar, Modifier.weight(1f).heightIn(min = Dimens.alvoToque).semantics { contentDescription = descricao }.testTag("livre-$hora"), enabled = habilitado) {
            Icon(IconesDieta.Mais, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Dimens.espacoPequeno))
            Text(stringResource(Res.string.agenda_livre))
        }
    }
}
