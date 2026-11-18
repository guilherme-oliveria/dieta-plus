package org.br.dietamais.ui.paciente.dieta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.app.SolicitacoesSecaoDieta
import org.br.dietamais.data.remoto.dto.OrientacaoResposta
import org.br.dietamais.data.remoto.dto.SuplementoResposta
import org.br.dietamais.dominio.agenda.formatarData
import org.br.dietamais.dominio.agenda.formatarHora
import org.br.dietamais.dominio.prescricoes.*
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AcoesPrescricoesPaciente(val aoAbrir: (SecaoPrescricaoPaciente) -> Unit = {}, val aoFechar: () -> Unit = {},
    val aoLembrete: (Uuid, Boolean) -> Unit = { _, _ -> }, val aoOrientacao: (Uuid) -> Unit = {})

fun acoesPrescricoesPaciente(vm: PrescricoesPacienteViewModel) = AcoesPrescricoesPaciente(vm::abrir, vm::fechar, vm::mudarLembrete, vm::alternarOrientacao)

/**
 * Aba Dieta do paciente com Suplementos e Orientações (MOB-93). Com uma das telas aberta, ela ocupa a aba; senão, a dieta aparece com o
 * cartão de atalho. O push "nova orientação" pede a seção por [SolicitacoesSecaoDieta].
 */
@Composable
fun DietaComPrescricoes(vm: PrescricoesPacienteViewModel, nomeNutri: String?, dieta: @Composable (extras: @Composable () -> Unit) -> Unit) {
    val estado by vm.estado.collectAsState()
    val pedido by SolicitacoesSecaoDieta.pendente.collectAsState()
    LaunchedEffect(pedido) { pedido?.let { vm.abrir(it); SolicitacoesSecaoDieta.consumir(it) } }
    val acoes = acoesPrescricoesPaciente(vm)
    when (estado.aberta) {
        SecaoPrescricaoPaciente.SUPLEMENTOS -> SuplementosPacienteTela(estado, acoes, nomeNutri)
        SecaoPrescricaoPaciente.ORIENTACOES -> OrientacoesPacienteTela(estado, acoes, nomeNutri)
        null -> dieta { PrescricoesResumoCard(estado, acoes) }
    }
}

/** Atalho na aba Dieta; some quando a nutri ainda não prescreveu nem orientou nada. */
@Composable
fun PrescricoesResumoCard(estado: EstadoPrescricoesPaciente, acoes: AcoesPrescricoesPaciente) {
    if (!estado.temAlgo) return
    CardDieta(Modifier.fillMaxWidth().testTag("prescricoes-resumo")) {
        if (estado.suplementos.isNotEmpty()) BotaoContornado(
            stringResource(Res.string.pac_presc_suplementos_n, stringResource(Res.string.pac_presc_em_uso, estado.suplementos.size)),
            { acoes.aoAbrir(SecaoPrescricaoPaciente.SUPLEMENTOS) }, Modifier.fillMaxWidth())
        if (estado.orientacoes.isNotEmpty()) BotaoContornado(
            if (estado.novas.isEmpty()) stringResource(Res.string.pac_presc_orientacoes)
            else stringResource(Res.string.pac_presc_orientacoes_n, if (estado.novas.size == 1) stringResource(Res.string.pac_presc_uma_nova) else stringResource(Res.string.pac_presc_novas, estado.novas.size)),
            { acoes.aoAbrir(SecaoPrescricaoPaciente.ORIENTACOES) }, Modifier.fillMaxWidth())
    }
}

/** MOB-93 (artboard `P-Suplementos`): o que tomar, quando, até quando, e o lembrete de cada um. */
@Composable
fun SuplementosPacienteTela(estado: EstadoPrescricoesPaciente, acoes: AcoesPrescricoesPaciente, nomeNutri: String?, modifier: Modifier = Modifier,
    fuso: TimeZone = TimeZone.currentSystemDefault()) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BarraTopo(stringResource(Res.string.pac_presc_suplementos), acoes.aoFechar, stringResource(Res.string.pac_presc_voltar),
            subtitulo = stringResource(Res.string.pac_presc_em_uso, estado.suplementos.size))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Dimens.paddingCard),
            verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
            if (estado.suplementos.isEmpty()) EstadoVazio(mensagem = stringResource(Res.string.pac_presc_sem_suplementos))
            estado.suplementos.forEach { s ->
                CartaoSuplementoPaciente(s, estado.proximas[s.id], estado.agora, s.id !in estado.semLembrete, { acoes.aoLembrete(s.id, it) }, fuso)
            }
            Text(nomeNutri?.let { stringResource(Res.string.pac_presc_rodape_nutri, it) } ?: stringResource(Res.string.pac_presc_rodape),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CartaoSuplementoPaciente(s: SuplementoResposta, proxima: Instant?, agora: Instant?, lembrar: Boolean, aoLembrar: (Boolean) -> Unit, fuso: TimeZone) {
    CardDieta(Modifier.fillMaxWidth().testTag("suplemento-paciente-${s.id}")) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.nome, style = MaterialTheme.typography.titleMedium)
            Text(s.forma?.let { stringResource(Res.string.pac_presc_dose_forma, s.dose, it) } ?: s.dose, style = MaterialTheme.typography.bodyMedium)
            val freq = s.frequencia.orEmpty(); val horas = horariosDe(s).joinToString(", ") { it.toString() }
            val linha = when { freq.isNotBlank() && horas.isNotBlank() -> stringResource(Res.string.pac_presc_freq_horas, freq, horas); freq.isNotBlank() -> freq; else -> horas }
            if (linha.isNotBlank()) Text(linha, style = MaterialTheme.typography.bodyMedium)
            s.fim?.let { Text(stringResource(Res.string.pac_presc_ate, formatarData(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (proxima != null && agora != null) {
                val hoje = agora.toLocalDateTime(fuso).date
                val dia = proxima.toLocalDateTime(fuso).date
                val hora = formatarHora(proxima, fuso)
                Text(when (dia) {
                    hoje -> stringResource(Res.string.pac_presc_proximo_hoje, hora)
                    hoje.plus(DatePeriod(days = 1)) -> stringResource(Res.string.pac_presc_proximo_amanha, hora)
                    else -> stringResource(Res.string.pac_presc_proximo_dia, formatarData(dia), hora)
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (horariosDe(s).isNotEmpty()) Row(Modifier.fillMaxWidth().heightIn(min = Dimens.alvoToque).toggleable(lembrar, role = Role.Switch, onValueChange = aoLembrar),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.pac_presc_lembrar), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(lembrar, null)
        }
    }
}

/** MOB-93 (artboard `P-Orientacoes`): a mais nova aberta; as anteriores por título e data, abrindo ao tocar. */
@Composable
fun OrientacoesPacienteTela(estado: EstadoPrescricoesPaciente, acoes: AcoesPrescricoesPaciente, nomeNutri: String?, modifier: Modifier = Modifier,
    fuso: TimeZone = TimeZone.currentSystemDefault()) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BarraTopo(stringResource(Res.string.pac_presc_orientacoes), acoes.aoFechar, stringResource(Res.string.pac_presc_voltar),
            subtitulo = nomeNutri?.let { stringResource(Res.string.pac_presc_da_nutri, it) })
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Dimens.paddingCard),
            verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
            val lista = estado.orientacoes
            if (lista.isEmpty()) EstadoVazio(mensagem = stringResource(Res.string.pac_presc_sem_orientacoes))
            lista.firstOrNull()?.let { CartaoOrientacao(it, aberta = true, nova = it.id in estado.destacadas, agora = estado.agora, fuso = fuso, aoTocar = null) }
            if (lista.size > 1) {
                Text(stringResource(Res.string.pac_presc_anteriores), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                lista.drop(1).forEach { o ->
                    CartaoOrientacao(o, aberta = estado.orientacaoAberta == o.id, nova = o.id in estado.destacadas, agora = estado.agora, fuso = fuso,
                        aoTocar = { acoes.aoOrientacao(o.id) })
                }
            }
        }
    }
}

@Composable
private fun CartaoOrientacao(o: OrientacaoResposta, aberta: Boolean, nova: Boolean, agora: Instant?, fuso: TimeZone, aoTocar: (() -> Unit)?) {
    CardDieta(Modifier.fillMaxWidth().testTag("orientacao-paciente-${o.id}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            if (nova) ChipStatus(EstiloStatus.AGORA, texto = stringResource(Res.string.pac_presc_nova))
            Text(dataOrientacao(o.criadoEm, agora, fuso), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(o.titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (aberta) TextoOrientacao(o.texto)
        else if (aoTocar != null) BotaoTexto(stringResource(Res.string.pac_presc_ler), aoTocar)
        if (aberta && aoTocar != null) BotaoTexto(stringResource(Res.string.pac_presc_recolher), aoTocar)
    }
}

@Composable
private fun dataOrientacao(criada: Instant, agora: Instant?, fuso: TimeZone): String {
    val dia = criada.toLocalDateTime(fuso).date
    val hoje = agora?.toLocalDateTime(fuso)?.date
    return if (dia == hoje) stringResource(Res.string.pac_presc_hoje_as, formatarHora(criada, fuso)) else formatarData(dia)
}

/** Negrito e lista (RN-ORI-01); nada vira link nem HTML. */
@Composable
fun TextoOrientacao(texto: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
        lerTextoOrientacao(texto).forEach { bloco ->
            val anotado = buildAnnotatedString {
                bloco.trechos.forEach { t -> if (t.negrito) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.texto) } else append(t.texto) }
            }
            when (bloco) {
                is BlocoTexto.Paragrafo -> Text(anotado, style = MaterialTheme.typography.bodyMedium)
                is BlocoTexto.ItemLista -> Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Text(anotado, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
