package org.br.dietamais.ui.nutri.dieta

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import org.br.dietamais.data.remoto.dto.GravidadeRestricao
import org.br.dietamais.data.remoto.dto.RestricaoSaudeDto
import org.br.dietamais.data.remoto.dto.TipoRestricaoSaude
import org.br.dietamais.dominio.dieta.AlertaItem
import org.br.dietamais.dominio.dieta.AlertasRestricao
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource

/** Faixa "Restrições da ficha" no topo do montador (artboard `A-Montador-Alerta`), com o lembrete de que o marcador é sugestão. */
@Composable
internal fun FaixaRestricoesFicha(restricoes: List<RestricaoSaudeDto>?) {
    val relevantes = restricoes?.let { AlertasRestricao.relevantes(it) }.orEmpty()
    if (relevantes.isEmpty()) return
    val partes = relevantes.map { r ->
        val base = stringResource(if (r.tipo == TipoRestricaoSaude.ALERGIA) Res.string.dieta_restricao_alergia else Res.string.dieta_restricao_intolerancia, r.nome)
        textoGravidade(r.gravidade)?.let { "$base ($it)" } ?: base
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().testTag("restricoes-ficha")) {
        Column(Modifier.padding(Dimens.espacoMedio), verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            Text(stringResource(Res.string.dieta_restricoes_ficha, partes.joinToString(", ")), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringResource(Res.string.dieta_restricoes_sugestao), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/** Alertas de um item: alergia pede revisão (bloqueia publicar até confirmar); intolerância só avisa. */
@Composable
internal fun AlertasDoItem(alertas: List<AlertaItem>, item: ItemEdicao, editavel: Boolean, aoRevisar: () -> Unit, aoTrocar: () -> Unit, aoDesfazer: () -> Unit) {
    if (alertas.isEmpty()) return
    val alergias = alertas.filter { it.exigeConfirmacao }
    val intolerancias = alertas.filterNot { it.exigeConfirmacao }
    if (alergias.isNotEmpty()) {
        val nomes = alergias.map { it.nome }.distinct().joinToString(", ")
        val gravidade = textoGravidade(alergias.first().gravidade)
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().testTag("alerta-alergia-${item.chave}")) {
            Column(Modifier.padding(Dimens.espacoPequeno)) {
                val texto = when {
                    item.alergiaConfirmada -> stringResource(Res.string.dieta_alergia_confirmada, nomes)
                    gravidade != null -> stringResource(Res.string.dieta_alergia_item, gravidade, nomes)
                    else -> stringResource(Res.string.dieta_alergia_item_sem_gravidade, nomes)
                }
                Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (editavel) FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                    if (item.alergiaConfirmada) BotaoTexto(stringResource(Res.string.dieta_alergia_desfazer), aoDesfazer)
                    else BotaoTexto(stringResource(Res.string.dieta_alergia_revisar), aoRevisar)
                    BotaoTexto(stringResource(Res.string.dieta_alergia_trocar), aoTrocar)
                }
            }
        }
    }
    if (intolerancias.isNotEmpty()) {
        val nomes = intolerancias.map { it.nome }.distinct().joinToString(", ")
        val gravidade = textoGravidade(intolerancias.first().gravidade)
        Text(if (gravidade != null) stringResource(Res.string.dieta_intolerancia_item, gravidade, nomes) else stringResource(Res.string.dieta_intolerancia_item_sem_gravidade, nomes),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("alerta-intolerancia-${item.chave}"))
    }
}

/** "N alergias sem confirmação: publicar fica bloqueado." perto dos botões. */
@Composable
internal fun AvisoAlergiasPendentes(quantidade: Int) {
    if (quantidade <= 0) return
    Text(if (quantidade == 1) stringResource(Res.string.dieta_alergia_pendentes_uma) else stringResource(Res.string.dieta_alergia_pendentes_varias, quantidade),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.testTag("alergias-pendentes").semantics { liveRegion = LiveRegionMode.Polite })
}

/** Folha "Manter um alimento com alergia?" (artboard `A-Montador-Confirmar`). */
@Composable
internal fun ConfirmarAlergiaDialogo(estado: EstadoDieta, acoes: AcoesMontador) {
    val (ref, chave) = estado.revisarAlergia ?: return
    val item = estado.rascunho?.refeicoes?.firstOrNull { it.chave == ref }?.itens?.firstOrNull { it.chave == chave } ?: return
    val alergias = estado.alertas[chave].orEmpty().filter { it.exigeConfirmacao }
    val alimento = estado.alimentos.firstOrNull { it.id == item.alimentoId }?.nome ?: stringResource(Res.string.dieta_alimento_desconhecido)
    val nomes = alergias.map { it.nome }.distinct().joinToString(", ")
    AlertDialog(onDismissRequest = acoes.aoCancelarAlergia, modifier = Modifier.testTag("confirmar-alergia"),
        title = { Text(stringResource(Res.string.dieta_alergia_confirmar_titulo)) },
        text = { Text(stringResource(Res.string.dieta_alergia_confirmar_texto, nomes, alimento)) },
        confirmButton = { BotaoPrincipal(stringResource(Res.string.dieta_alergia_manter), acoes.aoConfirmarAlergia) },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                BotaoTexto(stringResource(Res.string.dieta_cancelar), acoes.aoCancelarAlergia)
                BotaoTexto(stringResource(Res.string.dieta_alergia_trocar), { acoes.aoTrocarAlimento(ref, chave) }, Modifier.testTag("alergia-trocar-folha"))
            }
        })
}

@Composable
private fun textoGravidade(g: GravidadeRestricao?): String? = when (g) {
    GravidadeRestricao.LEVE -> stringResource(Res.string.saude_gravidade_leve)
    GravidadeRestricao.MODERADA -> stringResource(Res.string.saude_gravidade_moderada)
    GravidadeRestricao.GRAVE -> stringResource(Res.string.saude_gravidade_grave)
    else -> null
}
