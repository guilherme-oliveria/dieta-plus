package org.br.dietamais.ui.nutri.paciente

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.AdesaoDto
import org.br.dietamais.dominio.rotina.*
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.nutri.agenda.nomeDiaSemana
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ROTULOS_FOME = listOf(Res.string.humor_fome_1, Res.string.humor_fome_2, Res.string.humor_fome_3, Res.string.humor_fome_4, Res.string.humor_fome_5)
private val ROTULOS_DEPOIS = listOf(Res.string.humor_depois_1, Res.string.humor_depois_2, Res.string.humor_depois_3, Res.string.humor_depois_4, Res.string.humor_depois_5)

/** Liga o cartão ao ViewModel: busca ao abrir a ficha de outro paciente. */
@Composable
fun AdesaoHumorRota(viewModel: AdesaoPacienteViewModel, pacienteId: Uuid) {
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(pacienteId) { viewModel.abrir(pacienteId) }
    AdesaoHumorCard(estado, viewModel::recarregar)
}

/** MOB-89 (artboard `A-Adesao-Humor`): registros da semana, médias de fome e sensação e a lista do que saiu do plano. */
@Composable
fun AdesaoHumorCard(estado: EstadoAdesao, aoRecarregar: () -> Unit, modifier: Modifier = Modifier,
    fuso: TimeZone = TimeZone.currentSystemDefault(), agora: Instant = Clock.System.now()) {
    CardDieta(modifier.fillMaxWidth().testTag("adesao-humor")) {
        Text(stringResource(Res.string.adesao_titulo), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        val a = estado.adesao
        when {
            a != null -> ConteudoAdesao(a, fuso, agora)
            estado.carregando -> EstadoCarregando()
            estado.erro != null -> Column(verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                Text(stringResource(if (estado.erro == MensagemAdesao.SEM_CONEXAO) Res.string.adesao_sem_conexao else Res.string.adesao_erro),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                BotaoTexto(stringResource(Res.string.tentar_novamente), aoRecarregar, habilitado = !estado.semConexao)
            }
        }
    }
}

@Composable
private fun ConteudoAdesao(a: AdesaoDto, fuso: TimeZone, agora: Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Linha(stringResource(Res.string.adesao_registradas), stringResource(Res.string.adesao_registradas_valor, registradas(a.dias7), a.dias7.esperadas))
        HorizontalDivider()
        val (inicio, fim) = periodoDaJanela(a.dias7.dias, agora, fuso)
        val respostas = a.humor?.registros ?: 0
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            Text(stringResource(Res.string.adesao_humor), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(if (respostas == 1) stringResource(Res.string.adesao_humor_periodo_um, diaMes(inicio), diaMes(fim))
                else stringResource(Res.string.adesao_humor_periodo, diaMes(inicio), diaMes(fim), respostas),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (respostas == 0) Text(stringResource(Res.string.adesao_humor_vazio), style = MaterialTheme.typography.bodyMedium)
            else {
                Linha(stringResource(Res.string.adesao_fome), textoMedia(a.humor?.mediaFomeAntes, ROTULOS_FOME))
                Linha(stringResource(Res.string.adesao_depois), textoMedia(a.humor?.mediaSensacaoDepois, ROTULOS_DEPOIS))
                Text(stringResource(Res.string.adesao_humor_ajuda), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        val fora = foraDoPlanoOrdenado(a.foraDoPlano, fuso)
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.adesao_fora), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                if (fora.isNotEmpty()) Text(stringResource(Res.string.adesao_fora_qtd, fora.size), style = MaterialTheme.typography.bodySmall)
            }
            if (fora.isEmpty()) Text(stringResource(Res.string.adesao_fora_vazio), style = MaterialTheme.typography.bodyMedium)
            fora.forEach { (r, quando) ->
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
                    Text(r.texto, style = MaterialTheme.typography.bodyMedium)
                    val hora = "${quando.hour.toString().padStart(2, '0')}:${quando.minute.toString().padStart(2, '0')}"
                    Text(stringResource(Res.string.adesao_fora_quando, nomeDiaSemana(quando.date.dayOfWeek).lowercase(), diaMes(quando.date), hora),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun textoMedia(media: Double?, rotulos: List<StringResource>): String {
    val nivel = nivelDaMedia(media) ?: return stringResource(Res.string.adesao_sem_resposta)
    return stringResource(Res.string.adesao_media, mediaUmaCasa(media!!), stringResource(rotulos[nivel - 1]).lowercase())
}

@Composable
private fun Linha(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(rotulo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}
