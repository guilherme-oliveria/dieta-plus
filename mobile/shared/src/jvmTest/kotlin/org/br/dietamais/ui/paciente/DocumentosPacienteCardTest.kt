package org.br.dietamais.ui.paciente

import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.DocumentoPacienteResposta
import org.br.dietamais.data.remoto.dto.TipoDocumento
import org.br.dietamais.ui.paciente.documentos.DocumentosPacienteCard
import org.br.dietamais.ui.paciente.documentos.EstadoDocumentosPaciente
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class DocumentosPacienteCardTest {
    private val valido = DocumentoPacienteResposta(Uuid.random(), TipoDocumento.DECLARACAO_COMPARECIMENTO, "Declaração de comparecimento", Instant.parse("2026-11-17T12:00:00Z"), false, 1)
    private val anulado = DocumentoPacienteResposta(Uuid.random(), TipoDocumento.ATESTADO, "Atestado", Instant.parse("2026-11-03T12:00:00Z"), true, 1)

    @Test fun listaOfflineNosTresTamanhosComPdfSoDoValido() {
        listOf(Size(360f, 800f), Size(768f, 1024f), Size(1280f, 900f)).forEach { tamanho ->
            runSkikoComposeUiTest(size = tamanho) {
                setContent { DietaTheme { DocumentosPacienteCard(EstadoDocumentosPaciente(true, listOf(valido, anulado), semConexao = true), TimeZone.UTC) { Text("PDF-$it") } } }
                onNodeWithText("Sem internet. Você vê a lista, mas o PDF só abre conectado.").assertExists()
                onNodeWithText("17/11/2026").assertExists(); onNodeWithText("03/11/2026").assertExists()
                onNodeWithText("PDF-${valido.id}").assertExists(); onNodeWithText("PDF-${anulado.id}").assertDoesNotExist()
                onNodeWithText("Anulado").assertExists()
            }
        }
    }

    @Test fun semDocumentosNaoMostraNada() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        setContent { DietaTheme { DocumentosPacienteCard(EstadoDocumentosPaciente(true)) } }
        onNodeWithTag("documentos-paciente").assertDoesNotExist()
    }
}
