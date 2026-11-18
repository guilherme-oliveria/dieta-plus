package org.br.dietamais.ui.nutri.agenda

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.ConsultaResposta
import org.br.dietamais.data.remoto.dto.ModalidadeConsulta
import org.br.dietamais.data.remoto.dto.StatusConsulta
import org.br.dietamais.dominio.agenda.*
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.comum.icones.IconesDieta
import org.br.dietamais.ui.theme.Dimens
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

data class AcoesAgenda(val aoModo: (ModoAgenda) -> Unit = {}, val aoAnterior: () -> Unit = {}, val aoProximo: () -> Unit = {}, val aoHoje: () -> Unit = {}, val aoNova: () -> Unit = {},
    val aoRemarcar: (Uuid) -> Unit = {}, val aoCancelar: (Uuid) -> Unit = {}, val aoAtualizar: () -> Unit = {}, val aoFinanceiro: (Uuid) -> Unit = {},
    /** MOB-84 (RN-AGE-03): "Realizada" ou "Faltou". */
    val aoMarcar: (Uuid, StatusConsulta) -> Unit = { _, _ -> },
    /** MOB-85: escolher um dia no calendário do mês e tocar num horário livre. */
    val aoDia: (LocalDate) -> Unit = {}, val aoNovaEm: (LocalDate, Int) -> Unit = { _, _ -> },
    /** MOB-94: declaração de comparecimento da consulta realizada. */
    val aoDeclaracao: (Uuid) -> Unit = {})

/** Aba Agenda da nutri: dia, semana ou mês (MOB-85), com criar, remarcar e cancelar (MOB-26). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgendaTela(estado: EstadoAgenda, acoes: AcoesAgenda, fuso: TimeZone = TimeZone.currentSystemDefault(), agora: Instant = Clock.System.now(), rodape: @Composable ColumnScope.() -> Unit = {}) {
    val online = !estado.enviando
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.paddingCard), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Text(stringResource(Res.string.agenda_titulo), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        if (estado.semConexao) FaixaOffline()
        estado.erro?.let { Text(textoMensagemAgenda(it), color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        estado.aviso?.let { Text(textoMensagemAgenda(it), color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno), verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno), itemVerticalAlignment = Alignment.CenterVertically) {
            ModoAgenda.entries.forEach { modo ->
                ChipEscolha(estado.modo == modo, { acoes.aoModo(modo) }, label = { Text(stringResource(when (modo) { ModoAgenda.DIA -> Res.string.agenda_dia; ModoAgenda.SEMANA -> Res.string.agenda_semana; ModoAgenda.MES -> Res.string.agenda_mes })) })
            }
            BotaoPrincipal(stringResource(Res.string.agenda_nova), acoes.aoNova, habilitado = online && estado.pacientesAgendaveis.isNotEmpty())
            BotaoTexto(stringResource(Res.string.agenda_atualizar), acoes.aoAtualizar, habilitado = !estado.atualizando && !estado.semConexao)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            BotaoContornado(stringResource(Res.string.agenda_anterior), acoes.aoAnterior)
            Text(rotuloPeriodo(estado), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2)
            BotaoContornado(stringResource(Res.string.agenda_proximo), acoes.aoProximo)
        }
        BotaoTexto(stringResource(Res.string.agenda_hoje), acoes.aoHoje)
        if (estado.pacientesAgendaveis.isEmpty() && estado.consultas.isEmpty()) Text(stringResource(Res.string.agenda_form_sem_pacientes), style = MaterialTheme.typography.bodyMedium)
        when (estado.modo) {
            ModoAgenda.DIA -> {
                val doDia = consultasDoDia(estado.consultas, estado.dia, fuso)
                if (doDia.isEmpty()) EstadoVazio(mensagem = stringResource(Res.string.agenda_vazio_dia))
                else doDia.forEach { CartaoConsulta(it, estado, acoes, online, fuso, agora) }
            }
            ModoAgenda.SEMANA -> consultasDaSemana(estado.consultas, estado.dia, fuso).forEach { (dia, lista) ->
                Text(rotuloDia(dia), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                if (lista.isEmpty()) Text(stringResource(Res.string.agenda_vazio_semana_dia), style = MaterialTheme.typography.bodySmall)
                else lista.forEach { CartaoConsulta(it, estado, acoes, online, fuso, agora) }
            }
            ModoAgenda.MES -> AgendaMes(estado, acoes, online, fuso, agora) { CartaoConsulta(it, estado, acoes, online, fuso, agora) }
        }
        rodape()
    }
}

@Composable
private fun rotuloDia(dia: LocalDate) = nomeDiaSemana(dia.dayOfWeek) + " " + formatarData(dia)

@Composable
private fun rotuloPeriodo(estado: EstadoAgenda): String = when (estado.modo) {
    ModoAgenda.DIA -> rotuloDia(estado.dia)
    ModoAgenda.SEMANA -> diasDaSemana(estado.dia).let { formatarData(it.first()) + " – " + formatarData(it.last()) }
    ModoAgenda.MES -> rotuloMes(estado.dia)
}

@Composable
private fun CartaoConsulta(c: ConsultaResposta, estado: EstadoAgenda, acoes: AcoesAgenda, online: Boolean, fuso: TimeZone, agora: Instant) {
    val cancelada = c.status == StatusConsulta.CANCELADA
    val agendada = c.status == StatusConsulta.AGENDADA
    CardDieta(Modifier.fillMaxWidth().testTag("consulta-${c.id}")) {
        Text(stringResource(Res.string.agenda_horario, formatarHora(c.inicio, fuso), formatarHora(c.fim, fuso)), style = MaterialTheme.typography.titleMedium)
        Text(estado.nomeDo(c.pacienteId) ?: stringResource(Res.string.agenda_paciente_desconhecido), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMinimo)) {
            ChipStatus(EstiloStatus.INFO, texto = rotuloTipoConsulta(c.tipo))
            c.modalidade?.takeIf { it != ModalidadeConsulta.OUTRO }?.let { ChipStatus(EstiloStatus.DEPOIS, texto = rotuloModalidade(it)) }
            rotuloStatusConsulta(c.status)?.let { ChipStatus(if (cancelada || c.status == StatusConsulta.FALTA) EstiloStatus.ALERTA else EstiloStatus.FEITA, texto = it) }
        }
        c.lugar?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        c.linkOnline?.let { Text(stringResource(Res.string.agenda_link_rotulo, it.removePrefix("https://")), style = MaterialTheme.typography.bodyMedium) }
        c.orientacaoPaciente?.let { Text(stringResource(Res.string.agenda_orientacao_rotulo, it), style = MaterialTheme.typography.bodySmall, maxLines = 3) }
        c.observacaoInterna?.let { Text(stringResource(Res.string.agenda_observacao_rotulo, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3) }
        if (!cancelada) {
            // MOB-UX-08: a ação mais provável é o botão (futura: Remarcar; já passou ou encerrada: Financeiro); as outras ficam em "Mais ações".
            val passou = c.fim <= agora
            var menu by remember { mutableStateOf(false) }
            val financeiroLiberado = online && !estado.semConexao
            if (agendada && c.inicio <= agora) Text(stringResource(Res.string.agenda_como_foi), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                if (passou || !agendada) BotaoContornado(stringResource(Res.string.financeiro_abrir), { acoes.aoFinanceiro(c.id) }, habilitado = financeiroLiberado)
                else BotaoContornado(stringResource(Res.string.agenda_remarcar), { acoes.aoRemarcar(c.id) }, habilitado = online)
                Box {
                    IconButton({ menu = true }, Modifier.size(Dimens.alvoToque).testTag("mais-acoes-${c.id}")) { Icon(IconesDieta.MaisAcoes, contentDescription = stringResource(Res.string.agenda_mais_acoes)) }
                    DropdownMenu(menu, { menu = false }) {
                        // RN-AGE-03: Realizada e Faltou saem de Agendada e se corrigem entre si (gravam offline, como remarcar)
                        if (podeMarcar(c, StatusConsulta.REALIZADA)) DropdownMenuItem({ Text(stringResource(Res.string.agenda_marcar_realizada)) }, { menu = false; acoes.aoMarcar(c.id, StatusConsulta.REALIZADA) }, enabled = online)
                        if (podeMarcar(c, StatusConsulta.FALTA)) DropdownMenuItem({ Text(stringResource(Res.string.agenda_marcar_falta)) }, { menu = false; acoes.aoMarcar(c.id, StatusConsulta.FALTA) }, enabled = online)
                        if (c.status == StatusConsulta.REALIZADA) DropdownMenuItem({ Text(stringResource(Res.string.agenda_emitir_declaracao)) }, { menu = false; acoes.aoDeclaracao(c.id) }, enabled = financeiroLiberado)
                        if (agendada) {
                            if (passou) DropdownMenuItem({ Text(stringResource(Res.string.agenda_remarcar)) }, { menu = false; acoes.aoRemarcar(c.id) }, enabled = online)
                            else DropdownMenuItem({ Text(stringResource(Res.string.financeiro_abrir)) }, { menu = false; acoes.aoFinanceiro(c.id) }, enabled = financeiroLiberado)
                            DropdownMenuItem({ Text(stringResource(Res.string.agenda_cancelar), color = MaterialTheme.colorScheme.error) }, { menu = false; acoes.aoCancelar(c.id) }, enabled = online)
                        }
                    }
                }
            }
        }
    }
}
