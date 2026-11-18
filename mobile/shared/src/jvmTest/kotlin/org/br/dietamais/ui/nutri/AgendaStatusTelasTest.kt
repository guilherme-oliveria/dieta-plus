package org.br.dietamais.ui.nutri

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.agenda.FormularioConsulta
import org.br.dietamais.ui.nutri.agenda.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-84: artboards `A-Consulta-Form` e `A-Consulta-Status`. */
@OptIn(ExperimentalTestApi::class)
class AgendaStatusTelasTest {
    private val fuso = TimeZone.of("America/Sao_Paulo")
    private val ana = pacienteFalso("Ana Fictícia")
    private val dia = LocalDate(2026, 10, 28)
    private val depois = Instant.parse("2026-10-28T20:00:00Z")
    private val consulta = consultaFalsa(ana.id, Instant.parse("2026-10-28T17:30:00Z")).copy(modalidade = ModalidadeConsulta.ONLINE,
        linkOnline = "https://meet.exemplo.invalid/abc", orientacaoPaciente = "Jejum de 3 h", observacaoInterna = "Revisar adesão")

    @Test fun formularioOnlineMostraLinkETextosComAjuda() = runSkikoComposeUiTest(size = Size(390f, 1600f)) {
        val estado = EstadoAgenda(dia, pacientes = listOf(ana), formulario = FormularioConsulta(modalidade = ModalidadeConsulta.ONLINE, orientacao = "Venha em jejum"))
        setContent { DietaTheme { FormularioConsultaTela(estado, AcoesFormularioConsulta()) } }
        onNodeWithText("Avaliação").assertExists(); onNodeWithText("Online").assertExists()
        onNodeWithTag("consulta-link").assertExists(); onNodeWithText("Meet, Zoom ou WhatsApp, começando com https://").assertExists()
        onNodeWithText("O paciente vê este texto · 14/2000").assertExists(); onNodeWithText("Só você vê. Não vai para o paciente.").assertExists()
    }

    @Test fun presencialEscondeOLinkEModalidadeChamaAAcao() = runSkikoComposeUiTest(size = Size(390f, 1600f)) {
        var escolhida: ModalidadeConsulta? = null
        val estado = EstadoAgenda(dia, pacientes = listOf(ana), formulario = FormularioConsulta())
        setContent { DietaTheme { FormularioConsultaTela(estado, AcoesFormularioConsulta(aoModalidade = { escolhida = it })) } }
        onNodeWithTag("consulta-link").assertDoesNotExist(); onNodeWithText("Local (opcional)").assertExists()
        onNodeWithText("Online").performClick(); assertEquals(ModalidadeConsulta.ONLINE, escolhida)
    }

    @Test fun cartaoMostraModalidadeLinkTextosEOMenuMarcaRealizadaOuFalta() = runSkikoComposeUiTest(size = Size(390f, 1200f)) {
        var marcou: Pair<Uuid, StatusConsulta>? = null
        setContent { DietaTheme { AgendaTela(EstadoAgenda(dia, consultas = listOf(consulta), pacientes = listOf(ana)), AcoesAgenda(aoMarcar = { id, s -> marcou = id to s }), fuso, depois) } }
        onNodeWithText("Online").assertExists(); onNodeWithText("Link: meet.exemplo.invalid/abc").assertExists()
        onNodeWithText("Orientação ao paciente: Jejum de 3 h").assertExists(); onNodeWithText("Observação interna: Revisar adesão").assertExists()
        onNodeWithText("Como foi a consulta? Marcar aqui também atualiza o comparecimento no financeiro.").assertExists()
        onNodeWithContentDescription("Mais ações").performClick(); onNodeWithText("Faltou").performClick()
        assertEquals(consulta.id to StatusConsulta.FALTA, marcou)
    }

    @Test fun realizadaMostraSeloEsoCorrigeParaFalta() = runSkikoComposeUiTest(size = Size(390f, 1200f)) {
        val realizada = consulta.copy(status = StatusConsulta.REALIZADA)
        setContent { DietaTheme { AgendaTela(EstadoAgenda(dia, consultas = listOf(realizada), pacientes = listOf(ana)), AcoesAgenda(), fuso, depois) } }
        onNodeWithText("Realizada").assertExists(); onNodeWithText("Financeiro").assertExists()
        onNodeWithContentDescription("Mais ações").performClick()
        onNodeWithText("Faltou").assertExists(); onNodeWithText("Cancelar consulta").assertDoesNotExist(); onNodeWithText("Remarcar").assertDoesNotExist()
    }
}
