package org.br.dietamais.ui.paciente.hoje

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.br.dietamais.data.remoto.dto.RefeicaoResposta
import org.br.dietamais.dominio.rotina.*
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.paciente.dieta.EstadoHumor
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

private val ROTULOS_FOME = listOf(Res.string.humor_fome_1, Res.string.humor_fome_2, Res.string.humor_fome_3, Res.string.humor_fome_4, Res.string.humor_fome_5)
private val ROTULOS_DEPOIS = listOf(Res.string.humor_depois_1, Res.string.humor_depois_2, Res.string.humor_depois_3, Res.string.humor_depois_4, Res.string.humor_depois_5)

/** MOB-88 (artboard `P-Refeicao-Humor`): duas escalas opcionais depois de "Comi"; Pular ou Salvar. */
@Composable
fun FolhaHumor(humor: EstadoHumor, aoFome: (Int) -> Unit, aoSensacao: (Int) -> Unit, aoSalvar: () -> Unit, aoPular: () -> Unit) {
    AlertDialog(onDismissRequest = aoPular,
        title = { Text(stringResource(Res.string.humor_titulo, humor.nomeRefeicao.lowercase()), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
                Escala(stringResource(Res.string.humor_fome), ROTULOS_FOME, humor.fome, aoFome, "fome")
                Escala(stringResource(Res.string.humor_depois), ROTULOS_DEPOIS, humor.sensacao, aoSensacao, "depois")
                Text(stringResource(Res.string.humor_ajuda), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { BotaoPrincipal(stringResource(Res.string.humor_salvar), aoSalvar) },
        dismissButton = { BotaoTexto(stringResource(Res.string.humor_pular), aoPular) })
}

@Composable
private fun Escala(titulo: String, rotulos: List<StringResource>, escolhido: Int?, aoEscolher: (Int) -> Unit, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
        Text(titulo, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Dimens.espacoMinimo)) {
            rotulos.forEachIndexed { i, rotulo ->
                val valor = i + 1; val texto = stringResource(rotulo); val marcado = escolhido == valor
                val descricao = stringResource(Res.string.humor_valor_descricao, valor, texto)
                val cores = MaterialTheme.colorScheme; val forma = RoundedCornerShape(12.dp)
                Column(Modifier.weight(1f).heightIn(min = Dimens.alvoToque).clip(forma)
                    .background(if (marcado) cores.primaryContainer else cores.surface)
                    .border(if (marcado) 2.dp else 1.dp, if (marcado) cores.primary else cores.outlineVariant, forma)
                    .selectable(marcado, role = Role.RadioButton, onClick = { aoEscolher(valor) })
                    .semantics(mergeDescendants = true) { contentDescription = descricao }
                    .padding(vertical = Dimens.espacoPequeno, horizontal = 2.dp).testTag("$tag-$valor"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.clearAndSetSemantics { }) {
                        repeat(5) { n -> Box(Modifier.size(4.dp, 10.dp).clip(RoundedCornerShape(2.dp)).background(if (n < valor) cores.primary else cores.outlineVariant)) }
                    }
                    Text(texto, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.clearAndSetSemantics { })
                }
            }
        }
    }
}

/** MOB-88 (artboard `P-Fora-Plano`): texto curto, refeição opcional e horário; grava offline com desfazer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolhaForaDoPlano(formulario: FormularioForaDoPlano, erros: Set<ErroForaDoPlano>, refeicoes: List<RefeicaoResposta>,
    aoTexto: (String) -> Unit, aoRefeicao: (Uuid?) -> Unit, aoHora: (String) -> Unit, aoRegistrar: () -> Unit, aoCancelar: () -> Unit) {
    AlertDialog(onDismissRequest = aoCancelar,
        title = { Text(stringResource(Res.string.fora_titulo), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
                CampoTexto(stringResource(Res.string.fora_texto), formulario.texto, aoTexto, Modifier.fillMaxWidth().testTag("fora-texto"),
                    erro = if (ErroForaDoPlano.TEXTO in erros) stringResource(Res.string.fora_erro_texto) else null, singleLine = false)
                Text(stringResource(Res.string.fora_contador, formulario.texto.length, LIMITE_FORA_DO_PLANO), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
                Text(stringResource(Res.string.fora_refeicao), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno), verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                    ChipEscolha(formulario.refeicaoId == null, { aoRefeicao(null) }, label = { Text(stringResource(Res.string.fora_nenhuma)) })
                    refeicoes.forEach { r -> ChipEscolha(formulario.refeicaoId == r.id, { aoRefeicao(r.id) }, label = { Text(r.nome) }) }
                }
                CampoTexto(stringResource(Res.string.fora_hora), formulario.hora, aoHora, Modifier.fillMaxWidth().testTag("fora-hora"),
                    erro = if (ErroForaDoPlano.HORA in erros) stringResource(Res.string.fora_erro_hora) else null)
                Text(stringResource(Res.string.fora_nota), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { BotaoPrincipal(stringResource(Res.string.fora_registrar), aoRegistrar) },
        dismissButton = { BotaoTexto(stringResource(Res.string.fora_cancelar), aoCancelar) })
}
