package org.br.dietamais.ui.nutri.assinatura

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.assinatura.*
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.nutri.avaliacao.textoData
import org.br.dietamais.ui.theme.Dimens
import org.br.dietamais.ui.theme.FormasDieta
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

class AcoesAssinatura(val aoConfirmar: () -> Unit, val aoUrlAberta: () -> Unit, val aoRecarregar: () -> Unit,
    val aoPeriodicidade: (Periodicidade) -> Unit = {}, val aoEscolherPlano: (String) -> Unit = {})

fun acoesAssinatura(vm: AssinaturaViewModel) = AcoesAssinatura(vm::confirmar, vm::urlAberta, vm::recarregar, vm::mudarPeriodicidade, vm::escolherPlano)

/**
 * Cartão "Minha assinatura" da aba Conta (MOB-32) com a comparação de planos (MOB-96, artboards `A-Assinatura-Planos`, `A-Assinatura-Ativa`
 * e `A-Assinatura-Sem-Preco`). Compra só no navegador externo (RN-ASS-06). Preço e botão dependem da chave `exibePrecoEBotao` do servidor
 * (D-09), nunca da plataforma.
 */
@Composable
fun AssinaturaCard(estado: EstadoAssinatura, acoes: AcoesAssinatura, modifier: Modifier = Modifier) {
    val navegador = LocalUriHandler.current
    LaunchedEffect(estado.urlCheckout) { estado.urlCheckout?.let { navegador.openUri(it); acoes.aoUrlAberta() } }
    CardDieta(modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.assinatura_titulo), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        val a = estado.assinatura
        if (a == null) {
            if (estado.enviando) EstadoCarregando() else BotaoTexto(stringResource(Res.string.sync_atualizar), acoes.aoRecarregar, habilitado = !estado.semConexao)
        } else Detalhe(estado, a, acoes)
    }
}

@Composable
private fun Detalhe(estado: EstadoAssinatura, a: AssinaturaResposta, acoes: AcoesAssinatura) {
    Situacao(a)
    Uso(a.pacientesOcupados, a.plano.limitePacientes)
    if (estado.emAtraso) Text(stringResource(Res.string.assinatura_atraso, a.carenciaAte?.let { textoData(it) } ?: "—"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    if (estado.somenteLeitura) Text(stringResource(Res.string.assinatura_somente_leitura), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    val nomes = a.planosDisponiveis.associate { it.codigo to it.nome }
    a.trocaAgendada?.let { t ->
        Text(stringResource(Res.string.assinatura_troca_agendada, nomes[t.planoCodigo] ?: t.planoCodigo, textoData(t.aPartirDe)), style = MaterialTheme.typography.bodySmall)
    }
    estado.trocaAgendadaPara?.let {
        Text(stringResource(Res.string.assinatura_troca_pedida, textoData(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    estado.erro?.let {
        Text(stringResource(textoErro(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
    }
    when {
        a.planosDisponiveis.isEmpty() -> if (a.status != StatusAssinatura.ATIVA) Text(stringResource(Res.string.assinatura_pagamento_indisponivel), style = MaterialTheme.typography.bodySmall)
        !exibePreco(a) -> PlanosSemPreco(a.planosDisponiveis)
        else -> Comparacao(estado, a, acoes)
    }
}

@Composable
private fun Situacao(a: AssinaturaResposta) {
    if (a.status == StatusAssinatura.TESTE) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            ChipStatus(EstiloStatus.FEITA, texto = stringResource(Res.string.assinatura_teste_gratis))
            val dias = a.diasRestantesTeste
            when {
                dias != null -> Text(textoDiasTeste(dias), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("assinatura-dias"))
                a.fimPeriodoEm != null -> Text(stringResource(Res.string.assinatura_teste_ate, textoData(a.fimPeriodoEm)), style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    val periodo = stringResource(if (periodicidadeAtual(a) == Periodicidade.ANUAL) Res.string.assinatura_anual_min else Res.string.assinatura_mensal_min)
    Text(stringResource(Res.string.assinatura_plano_periodo, a.plano.nome, periodo), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(rotuloStatus(a.status)), style = MaterialTheme.typography.bodyMedium)
    val fim = a.fimPeriodoEm ?: return
    val valor = (a.planosDisponiveis.firstOrNull { it.codigo == a.plano.codigo } ?: a.plano).let { precoDe(it, periodicidadeAtual(a)) }
    if (a.status == StatusAssinatura.ATIVA && valor != null && exibePreco(a))
        Text(stringResource(Res.string.assinatura_proxima_cobranca, textoData(fim), preco(valor)), style = MaterialTheme.typography.bodySmall)
    else Text(stringResource(Res.string.assinatura_periodo_ate, textoData(fim)), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun textoDiasTeste(dias: Int) = when (dias) {
    0 -> stringResource(Res.string.assinatura_teste_termina_hoje)
    1 -> stringResource(Res.string.assinatura_falta_um_dia)
    else -> stringResource(Res.string.assinatura_faltam_dias, dias)
}

@Composable
private fun Uso(ocupados: Int, limite: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.espacoMinimo)) {
        Text(if (limite == null) stringResource(Res.string.assinatura_uso_sem_limite, ocupados) else stringResource(Res.string.assinatura_uso, ocupados, limite),
            style = MaterialTheme.typography.bodyMedium)
        fracaoUso(ocupados, limite)?.let { f ->
            val perto = pertoDoLimite(ocupados, limite)
            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth(),
                color = if (perto) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            if (perto && limite != null) Text(stringResource(Res.string.assinatura_perto_limite, limite), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** D-09: a loja não deixa mostrar preço nem botão. Só os nomes e os limites, como no artboard `A-Assinatura-Sem-Preco`. */
@Composable
private fun PlanosSemPreco(planos: List<PlanoResposta>) {
    Text(stringResource(Res.string.assinatura_planos), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    planos.forEach { p ->
        Text(p.limitePacientes?.let { stringResource(Res.string.assinatura_plano_limite_item, p.nome, it) } ?: stringResource(Res.string.assinatura_plano_sem_limite_item, p.nome),
            style = MaterialTheme.typography.bodyMedium)
    }
    Text(stringResource(Res.string.assinatura_gerenciada_fora), style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Comparacao(estado: EstadoAssinatura, a: AssinaturaResposta, acoes: AcoesAssinatura) {
    val habilitado = !estado.semConexao && !estado.enviando
    if (temAnual(a.planosDisponiveis)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno), verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
            listOf(Periodicidade.MENSAL to Res.string.assinatura_mensal, Periodicidade.ANUAL to Res.string.assinatura_anual).forEach { (p, rotulo) ->
                ChipEscolha(selected = estado.periodicidade == p, onClick = { acoes.aoPeriodicidade(p) }, label = { Text(stringResource(rotulo)) }, enabled = habilitado,
                    modifier = Modifier.heightIn(min = Dimens.alvoToque).testTag("periodicidade-${p.name}"))
            }
            mesesGratisNoAnual(a.planosDisponiveis)?.let { m ->
                Text(if (m == 1) stringResource(Res.string.assinatura_um_mes_gratis) else stringResource(Res.string.assinatura_meses_gratis, m),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
        a.planosDisponiveis.forEach { p ->
            CartaoPlano(p, estado.periodicidade, escolhido = p.codigo == estado.planoEscolhido,
                seu = a.status == StatusAssinatura.ATIVA && p.codigo == a.plano.codigo && estado.periodicidade == periodicidadeAtual(a),
                habilitado = habilitado, aoEscolher = { acoes.aoEscolherPlano(p.codigo) })
        }
    }
    val plano = estado.plano
    val rotulo = when (estado.acao) {
        AcaoPlano.ASSINAR -> plano?.let { stringResource(if (estado.periodicidade == Periodicidade.ANUAL) Res.string.assinatura_assinar_anual else Res.string.assinatura_assinar_mensal, it.nome) }
        AcaoPlano.MUDAR -> plano?.let { stringResource(Res.string.assinatura_mudar_para, it.nome) }
        AcaoPlano.MANTER_ATUAL -> plano?.let { stringResource(Res.string.assinatura_manter_atual, it.nome) }
        AcaoPlano.SEU_PLANO, AcaoPlano.INDISPONIVEL -> null
    }
    rotulo?.let { BotaoPrincipal(it, acoes.aoConfirmar, Modifier.fillMaxWidth().testTag("assinatura-confirmar"), habilitado = habilitado, carregando = estado.enviando) }
    if (estado.acao == AcaoPlano.ASSINAR || estado.acao == AcaoPlano.MUDAR) Text(stringResource(Res.string.assinatura_navegador), style = MaterialTheme.typography.bodySmall)
    if (a.status == StatusAssinatura.ATIVA) Text(stringResource(Res.string.assinatura_troca_regra), style = MaterialTheme.typography.bodySmall)
    if (!a.pagamentoDisponivel) Text(stringResource(Res.string.assinatura_pagamento_indisponivel), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun CartaoPlano(p: PlanoResposta, periodicidade: Periodicidade, escolhido: Boolean, seu: Boolean, habilitado: Boolean, aoEscolher: () -> Unit) {
    val cores = MaterialTheme.colorScheme
    val valor = precoDe(p, periodicidade)
    Surface(shape = FormasDieta.card, color = if (escolhido) cores.primaryContainer.copy(alpha = 0.35f) else cores.surface,
        border = BorderStroke(if (escolhido) Dimens.borda * 2 else Dimens.borda, if (escolhido) cores.primary else cores.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.alvoToque).testTag("plano-${p.codigo}")
            .selectable(selected = escolhido, enabled = habilitado, role = Role.RadioButton, onClick = aoEscolher)) {
        Column(Modifier.padding(Dimens.paddingCard), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMinimo)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                Text(p.nome, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                if (seu) ChipStatus(EstiloStatus.FEITA, texto = stringResource(Res.string.assinatura_seu_plano))
            }
            Text(when {
                valor == null -> stringResource(Res.string.assinatura_so_mensal)
                periodicidade == Periodicidade.ANUAL -> stringResource(Res.string.assinatura_preco_ano, preco(valor), preco(mensalEquivalente(p) ?: valor))
                else -> stringResource(Res.string.assinatura_preco_mes, preco(valor))
            }, style = MaterialTheme.typography.bodyMedium, color = if (valor == null) cores.onSurfaceVariant else cores.onSurface)
            Text(p.limitePacientes?.let { stringResource(Res.string.assinatura_ate_pacientes, it) } ?: stringResource(Res.string.assinatura_sem_limite),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** `R$ 1.799,00`: milhar com ponto e centavos com vírgula. */
internal fun preco(valor: Double): String {
    val centavos = (valor * 100).roundToLong()
    val reais = (centavos / 100).toString().reversed().chunked(3).joinToString(".").reversed()
    return "R$ $reais,${(centavos % 100).toString().padStart(2, '0')}"
}

private fun rotuloStatus(s: StatusAssinatura): StringResource = when (s) {
    StatusAssinatura.TESTE -> Res.string.assinatura_status_teste
    StatusAssinatura.ATIVA -> Res.string.assinatura_status_ativa
    StatusAssinatura.EM_ATRASO -> Res.string.assinatura_status_atraso
    StatusAssinatura.CANCELADA -> Res.string.assinatura_status_cancelada
    StatusAssinatura.EXPIRADA -> Res.string.assinatura_status_expirada
    StatusAssinatura.OUTRO -> Res.string.assinatura_status_outro
}

private fun textoErro(m: MensagemAssinatura): StringResource = when (m) {
    MensagemAssinatura.SEM_CONEXAO -> Res.string.perfil_erro_sem_conexao
    MensagemAssinatura.JA_ATIVA -> Res.string.assinatura_erro_ja_ativa
    MensagemAssinatura.PAGAMENTO_INDISPONIVEL -> Res.string.assinatura_pagamento_indisponivel
    MensagemAssinatura.PLANO_INDISPONIVEL -> Res.string.assinatura_erro_plano_indisponivel
    MensagemAssinatura.ERRO -> Res.string.perfil_erro_geral
}
