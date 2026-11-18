package org.br.dietamais.ui.paciente.metas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.plus
import org.br.dietamais.data.remoto.dto.FrequenciaMeta
import org.br.dietamais.data.remoto.dto.TipoMeta
import org.br.dietamais.dominio.metas.ProgressoMeta
import org.br.dietamais.dominio.metas.SituacaoDia
import org.br.dietamais.dominio.metas.segundaDe
import org.br.dietamais.dominio.metas.textoAlvo
import org.br.dietamais.dominio.rotina.diaMes
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.nutri.metas.descricaoMeta
import org.br.dietamais.ui.nutri.metas.tituloMeta
import org.br.dietamais.ui.nutri.metas.unidadeTipo
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

class AcoesMetasPaciente(val aoSomar: (Uuid, Double) -> Unit = { _, _ -> }, val aoCumpri: (Uuid) -> Unit = {}, val aoAbrirOutro: (Uuid) -> Unit = {},
    val aoTextoOutro: (String) -> Unit = {}, val aoConfirmarOutro: () -> Unit = {}, val aoAbrirSemana: () -> Unit = {}, val aoFecharSemana: () -> Unit = {},
    val aoTentarNovamente: () -> Unit = {})

fun acoesMetasPaciente(vm: MetasPacienteViewModel) = AcoesMetasPaciente(vm::somar, vm::cumpriHoje, vm::abrirOutro, vm::mudarTextoOutro, vm::confirmarOutro,
    vm::abrirSemana, vm::fecharSemana, vm::tentarNovamente)

@Composable
fun MetasPacienteRota(vm: MetasPacienteViewModel) {
    val estado by vm.estado.collectAsState()
    val acoes = remember(vm) { acoesMetasPaciente(vm) }
    MetasHoje(estado, acoes)
    if (estado.verSemana) TelaSobreposta(acoes.aoFecharSemana) { MetasSemanaTela(estado, acoes) }
}

/** Valor do botão rápido por tipo ("+ 10 min", "+ 1 porção"); SIM_NAO usa "Cumpri hoje" e OUTRO só o valor livre. */
fun passoRapido(tipo: TipoMeta): Double? = when (tipo) {
    TipoMeta.ATIVIDADE_MIN -> 10.0
    TipoMeta.PASSOS -> 1000.0
    TipoMeta.SONO_HORAS, TipoMeta.FRUTAS_VERDURAS, TipoMeta.CONTADOR -> 1.0
    else -> null
}

/** MOB-90 (artboard `P-Hoje-Metas`): "Metas de hoje" no Hoje, com registro rápido, valor livre e sequência. Some se não há metas. */
@Composable
fun MetasHoje(estado: EstadoMetasPaciente, acoes: AcoesMetasPaciente, modifier: Modifier = Modifier) {
    if (!estado.carregado || (estado.hoje.isEmpty() && estado.erro == null)) return
    Column(modifier.fillMaxWidth().testTag("metas-hoje"), verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.pacmeta_titulo), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            if (estado.semana.isNotEmpty()) BotaoTexto(stringResource(Res.string.pacmeta_ver_semana), acoes.aoAbrirSemana)
        }
        if (estado.erro != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.pacmeta_erro), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Assertive })
            BotaoTexto(stringResource(Res.string.pacmeta_tentar), acoes.aoTentarNovamente)
        }
        estado.hoje.forEach { p -> CartaoMetaHoje(p, estado, acoes) }
    }
}

@Composable
private fun CartaoMetaHoje(p: ProgressoMeta, estado: EstadoMetasPaciente, acoes: AcoesMetasPaciente) {
    val m = p.meta
    val titulo = tituloMeta(m.tipo, m.nome)
    CardDieta(Modifier.fillMaxWidth().testTag("meta-${m.id}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            textoSequencia(p)?.let { ChipStatus(EstiloStatus.FEITA, texto = it) }
        }
        if (m.tipo == TipoMeta.SIM_NAO) {
            Text(descricaoMeta(m.tipo, m.alvo, m.unidade, m.frequencia, m.vezesPorSemana, false), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.cumpridaHoje) ChipStatus(EstiloStatus.FEITA, texto = stringResource(Res.string.pacmeta_cumprida))
            else BotaoContornado(stringResource(Res.string.pacmeta_cumpri), { acoes.aoCumpri(m.id) }, Modifier.fillMaxWidth())
            textoSemana(p)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        } else ConteudoQuantidade(p, titulo, estado, acoes)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ConteudoQuantidade(p: ProgressoMeta, titulo: String, estado: EstadoMetasPaciente, acoes: AcoesMetasPaciente) {
    val m = p.meta
    val unidade = unidadeTipo(m.tipo, m.unidade)
    val feito = textoAlvo(p.hoje)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Text("$feito $unidade".trim(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(stringResource(Res.string.pacmeta_de_alvo, "${textoAlvo(m.alvo)} $unidade".trim()), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    LinearProgressIndicator(progress = { (p.hoje / m.alvo).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    textoSemana(p)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
        passoRapido(m.tipo)?.let { passo ->
            val un = if (m.tipo == TipoMeta.FRUTAS_VERDURAS) stringResource(Res.string.pacmeta_un_porcao) else unidade
            BotaoTexto(stringResource(Res.string.pacmeta_somar, "${textoAlvo(passo)} $un".trim()), { acoes.aoSomar(m.id, passo) },
                Modifier.semantics { contentDescription = "${textoAlvo(passo)} $un, $titulo".trim() })
        }
        BotaoTexto(stringResource(Res.string.pacmeta_outro), { acoes.aoAbrirOutro(m.id) }, selecionado = estado.outroValor == m.id)
    }
    if (estado.outroValor == m.id) {
        CampoNumero(stringResource(Res.string.pacmeta_outro_rotulo, unidade.ifBlank { titulo }), estado.textoOutro, acoes.aoTextoOutro, Modifier.fillMaxWidth(),
            erro = if (estado.erroOutro) stringResource(Res.string.pacmeta_outro_invalido) else null, ultimo = true)
        BotaoPrincipal(stringResource(Res.string.pacmeta_outro_adicionar), acoes.aoConfirmarOutro)
    }
}

/** "6 dias seguidos" (diária) ou "2 semanas seguidas" (semanal); só a partir de 2, como na ficha da nutri. */
@Composable
internal fun textoSequencia(p: ProgressoMeta): String? = p.sequencia.takeIf { it > 1 }?.let {
    stringResource(if (p.meta.frequencia == FrequenciaMeta.SEMANAL) Res.string.pacmeta_seq_semanas else Res.string.pacmeta_seq_dias, it)
}

/** Semanal: "1 de 3 na semana · faltam 2"; diária: nada no Hoje (o dia já mostra o progresso). */
@Composable
internal fun textoSemana(p: ProgressoMeta): String? {
    if (p.meta.frequencia != FrequenciaMeta.SEMANAL) return null
    val vezes = p.meta.vezesPorSemana ?: 1
    return if (p.faltamNaSemana == 0) stringResource(Res.string.pacmeta_semanal_completa, minOf(p.semana.diasCumpridos, vezes), vezes)
    else stringResource(Res.string.pacmeta_semanal_faltam, p.semana.diasCumpridos, vezes, p.faltamNaSemana)
}

/** MOB-90 (artboard `P-Metas-Semana`): cada meta com os 7 dias, a sequência e o progresso da semana. */
@Composable
fun MetasSemanaTela(estado: EstadoMetasPaciente, acoes: AcoesMetasPaciente) {
    val hoje = estado.hojeData ?: return
    val segunda = segundaDe(hoje)
    val domingo = segunda.plus(kotlinx.datetime.DatePeriod(days = 6))
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            BarraTopo(stringResource(Res.string.pacmeta_semana_titulo), acoes.aoFecharSemana, stringResource(Res.string.pacmeta_voltar),
                subtitulo = stringResource(Res.string.pacmeta_semana_periodo, diaMes(segunda), diaMes(domingo), nomeDia(hoje.dayOfWeek)))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.paddingCard).testTag("metas-semana"),
                verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
                estado.semana.forEach { p -> CartaoMetaSemana(p) }
                Text(stringResource(Res.string.pacmeta_semana_ajuda), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CartaoMetaSemana(p: ProgressoMeta) {
    val m = p.meta
    CardDieta(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tituloMeta(m.tipo, m.nome), style = MaterialTheme.typography.titleSmall)
                Text(descricaoMeta(m.tipo, m.alvo, m.unidade, m.frequencia, m.vezesPorSemana, false), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            textoSequencia(p)?.let { ChipStatus(EstiloStatus.FEITA, texto = it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            p.dias.forEach { (dia, situacao) -> BolinhaDia(letraDia(dia.dayOfWeek), situacao, "${nomeDia(dia.dayOfWeek)} ${diaMes(dia)}") }
        }
        Text(textoSemana(p) ?: stringResource(Res.string.pacmeta_dias_ate_agora, p.semana.diasCumpridos, p.semana.diasPrevistos),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BolinhaDia(letra: String, situacao: SituacaoDia, extenso: String) {
    val cores = MaterialTheme.colorScheme
    val rotulo = stringResource(when (situacao) {
        SituacaoDia.CUMPRIDO -> Res.string.pacmeta_sit_cumprido
        SituacaoDia.NAO_CUMPRIDO -> Res.string.pacmeta_sit_nao
        SituacaoDia.ABERTO -> Res.string.pacmeta_sit_aberto
        SituacaoDia.FUTURO -> Res.string.pacmeta_sit_futuro
        SituacaoDia.FORA -> Res.string.pacmeta_sit_fora
    })
    val descricao = stringResource(Res.string.pacmeta_dia_descricao, extenso, rotulo)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.espacoMinimo),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = descricao }) {
        Text(letra, style = MaterialTheme.typography.labelSmall, color = cores.onSurfaceVariant)
        val (fundo, borda) = when (situacao) {
            SituacaoDia.CUMPRIDO -> cores.primary to cores.primary
            SituacaoDia.ABERTO -> cores.surface to cores.primary
            SituacaoDia.NAO_CUMPRIDO -> cores.surfaceVariant to cores.outline
            SituacaoDia.FUTURO, SituacaoDia.FORA -> cores.surface to cores.outlineVariant
        }
        Box(Modifier.size(28.dp).background(fundo, CircleShape).border(if (situacao == SituacaoDia.ABERTO) 2.dp else 1.dp, borda, CircleShape))
    }
}

@Composable
private fun letraDia(d: DayOfWeek) = stringResource(when (d) {
    DayOfWeek.MONDAY -> Res.string.pacmeta_letra_seg; DayOfWeek.TUESDAY -> Res.string.pacmeta_letra_ter
    DayOfWeek.WEDNESDAY -> Res.string.pacmeta_letra_qua; DayOfWeek.THURSDAY -> Res.string.pacmeta_letra_qui
    DayOfWeek.FRIDAY -> Res.string.pacmeta_letra_sex; DayOfWeek.SATURDAY -> Res.string.pacmeta_letra_sab
    DayOfWeek.SUNDAY -> Res.string.pacmeta_letra_dom
})

@Composable
private fun nomeDia(d: DayOfWeek): String = stringResource(when (d) {
    DayOfWeek.MONDAY -> Res.string.pac_dia_longo_seg; DayOfWeek.TUESDAY -> Res.string.pac_dia_longo_ter
    DayOfWeek.WEDNESDAY -> Res.string.pac_dia_longo_qua; DayOfWeek.THURSDAY -> Res.string.pac_dia_longo_qui
    DayOfWeek.FRIDAY -> Res.string.pac_dia_longo_sex; DayOfWeek.SATURDAY -> Res.string.pac_dia_longo_sab
    DayOfWeek.SUNDAY -> Res.string.pac_dia_longo_dom
})
