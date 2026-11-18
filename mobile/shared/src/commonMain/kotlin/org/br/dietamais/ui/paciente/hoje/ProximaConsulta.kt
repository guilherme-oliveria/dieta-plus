package org.br.dietamais.ui.paciente.hoje

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.br.dietamais.data.remoto.dto.ModalidadeConsulta
import org.br.dietamais.dominio.agenda.consultaDoPaciente
import org.br.dietamais.ui.comum.BotaoPrincipal
import org.br.dietamais.ui.comum.ChipStatus
import org.br.dietamais.ui.comum.EstiloStatus
import org.br.dietamais.ui.nutri.agenda.rotuloModalidade
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.ConsultaResposta
import org.br.dietamais.data.repositorio.PortaConsultas
import org.br.dietamais.dominio.agenda.diaLocal
import org.br.dietamais.dominio.agenda.formatarData
import org.br.dietamais.dominio.agenda.formatarHora
import org.br.dietamais.dominio.agenda.proximaConsulta
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.CardDieta
import org.br.dietamais.ui.nutri.agenda.rotuloTipoConsulta
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/** Consultas do paciente, lidas do banco local (o sync as traz); a escolha da próxima fica por conta de quem desenha. */
class ConsultasPacienteViewModel(consultas: PortaConsultas, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : ViewModel() {
    private val atual = MutableStateFlow<List<ConsultaResposta>>(emptyList())
    val consultas = atual.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) { consultas.observar().catch { }.collect { atual.value = it } }
    }
}

/**
 * Cartão da próxima consulta no Hoje; some quando não há consulta marcada.
 * MOB-87 (artboard `P-Proxima-Consulta`, D-03): modalidade, "Entrar na consulta" quando é online com link, e a orientação da nutri.
 * O paciente nunca vê a observação interna nem a ação "Falta".
 */
@Composable
fun ProximaConsultaCard(consultas: List<ConsultaResposta>, agora: Instant = Clock.System.now(), fuso: TimeZone = TimeZone.currentSystemDefault(), modifier: Modifier = Modifier,
    aoEntrar: ((String) -> Unit)? = null) {
    val proxima = proximaConsulta(consultas, agora) ?: return
    val visao = consultaDoPaciente(proxima, fuso)
    val navegador = LocalUriHandler.current
    val entrar = aoEntrar ?: { url: String -> try { navegador.openUri(url) } catch (_: Exception) { } }
    CardDieta(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.agenda_prox_titulo), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            ChipStatus(if (visao.modalidade == ModalidadeConsulta.ONLINE) EstiloStatus.DEPOIS else EstiloStatus.INFO, texto = rotuloModalidade(visao.modalidade))
        }
        val dia = diaLocal(proxima.inicio, fuso); val hoje = diaLocal(agora, fuso); val hora = formatarHora(proxima.inicio, fuso)
        Text(when (dia) {
            hoje -> stringResource(Res.string.agenda_prox_hoje, hora)
            hoje.plus(1, DateTimeUnit.DAY) -> stringResource(Res.string.agenda_prox_amanha, hora)
            else -> stringResource(Res.string.agenda_prox_data, formatarData(dia), hora)
        }, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.agenda_prox_tipo_duracao, rotuloTipoConsulta(visao.tipo), visao.duracaoMin), style = MaterialTheme.typography.bodyMedium)
        visao.lugar?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        visao.link?.let { link ->
            BotaoPrincipal(stringResource(Res.string.agenda_prox_entrar), { entrar(link) }, Modifier.fillMaxWidth())
            Text(stringResource(Res.string.agenda_prox_entrar_ajuda), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visao.orientacao?.let {
            Column(Modifier.fillMaxWidth().padding(top = Dimens.espacoMinimo), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMinimo)) {
                Text(stringResource(Res.string.agenda_prox_orientacao), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ProximaConsultaRota(viewModel: ConsultasPacienteViewModel, modifier: Modifier = Modifier) {
    val lista by viewModel.consultas.collectAsState()
    ProximaConsultaCard(lista, modifier = modifier.padding(horizontal = Dimens.paddingCard, vertical = Dimens.espacoPequeno))
}
