package org.br.dietamais.ui.paciente.documentos

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.data.remoto.dto.DocumentoPacienteResposta
import org.br.dietamais.data.repositorio.PortaPdf
import org.br.dietamais.data.repositorio.RecursoPdf
import org.br.dietamais.dominio.agenda.formatarData
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

/** Cartão "Documentos" no Perfil do paciente (MOB-95); some quando não há nenhum. */
@Composable
fun DocumentosPacienteRota(vm: DocumentosPacienteViewModel, pdf: PortaPdf) {
    val estado by vm.estado.collectAsState()
    DocumentosPacienteCard(estado) { id -> BotaoPdf(stringResource(Res.string.pac_doc_abrir), !estado.semConexao, { pdf.link(RecursoPdf.DOCUMENTO, id) }) }
}

/** MOB-95 (artboard `P-Documentos`): lista offline; o PDF só abre conectado. */
@Composable
fun DocumentosPacienteCard(estado: EstadoDocumentosPaciente, fuso: TimeZone = TimeZone.currentSystemDefault(), abrir: @Composable (Uuid) -> Unit = {}) {
    if (estado.documentos.isEmpty()) return
    CardDieta(Modifier.fillMaxWidth().testTag("documentos-paciente")) {
        Text(stringResource(Res.string.pac_doc_titulo), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (estado.semConexao) Text(stringResource(Res.string.pac_doc_sem_internet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        estado.documentos.forEach { d -> LinhaDocumento(d, fuso, abrir) }
    }
}

@Composable
private fun LinhaDocumento(d: DocumentoPacienteResposta, fuso: TimeZone, abrir: @Composable (Uuid) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Dimens.espacoPequeno).testTag("documento-paciente-${d.id}"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
            Text(d.titulo, style = MaterialTheme.typography.titleSmall)
            Text(formatarData(d.emitidoEm.toLocalDateTime(fuso).date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (d.anulado) ChipStatus(EstiloStatus.ALERTA, texto = stringResource(Res.string.pac_doc_anulado)) else abrir(d.id)
    }
}
