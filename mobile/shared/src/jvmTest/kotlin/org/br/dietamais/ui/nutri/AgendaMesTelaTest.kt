package org.br.dietamais.ui.nutri

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.ui.nutri.agenda.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.time.Instant

/** MOB-85 (artboard `A-Agenda-Mes`): calendário do mês, lista do dia e horário livre que abre a nova consulta, nos 3 tamanhos. */
@OptIn(ExperimentalTestApi::class)
class AgendaMesTelaTest {
    private val fuso = TimeZone.of("America/Sao_Paulo")
    private val carlos = pacienteFalso("Carlos Fictício"); private val bruna = pacienteFalso("Bruna Fictícia")
    private val dia = LocalDate(2026, 11, 19)
    private val agora = Instant.parse("2026-11-17T12:00:00Z")
    private val nove = consultaFalsa(carlos.id, Instant.parse("2026-11-19T12:00:00Z"), 50, TipoConsulta.PRIMEIRA)
    private val quinze = consultaFalsa(bruna.id, Instant.parse("2026-11-19T18:00:00Z"), 50)
    private val dia3 = consultaFalsa(bruna.id, Instant.parse("2026-11-03T13:00:00Z"), 50)
    private val estado = EstadoAgenda(dia, ModoAgenda.MES, listOf(nove, quinze, dia3), listOf(carlos, bruna))

    private fun mostraMesEListaDoDia(size: Size) = runSkikoComposeUiTest(size = size) {
        setContent { DietaTheme { AgendaTela(estado, AcoesAgenda(), fuso, agora) } }
        onNodeWithText("Novembro de 2026").assertIsDisplayed()
        onNodeWithContentDescription("19 de novembro, 2 consultas").assertExists().assertIsSelected()
        onNodeWithContentDescription("3 de novembro, 1 consulta").assertExists()
        onNodeWithContentDescription("17 de novembro, livre, hoje").assertExists()
        onNodeWithContentDescription("30 de novembro, livre").assertExists()
        onNodeWithTag("dia-mes-2026-12-01").assertDoesNotExist()
        onNodeWithText("qui, 19/11").assertExists(); onNodeWithText("2 consultas").assertExists()
        onNodeWithText("Carlos Fictício").assertExists(); onNodeWithText("Bruna Fictícia").assertExists()
        onNodeWithContentDescription("Nova consulta qui 19/11 às 10:00").assertExists().assertIsEnabled()
    }

    @Test fun celular() = mostraMesEListaDoDia(Size(390f, 2400f))
    @Test fun tablet() = mostraMesEListaDoDia(Size(834f, 1600f))
    @Test fun desktop() = mostraMesEListaDoDia(Size(1280f, 1400f))

    @Test fun tocarNoDiaENoHorarioLivreChamaAsAcoes() = runSkikoComposeUiTest(size = Size(390f, 2400f)) {
        var escolhido: LocalDate? = null; var nova: Pair<LocalDate, Int>? = null
        setContent { DietaTheme { AgendaTela(estado, AcoesAgenda(aoDia = { escolhido = it }, aoNovaEm = { d, h -> nova = d to h }), fuso, agora) } }
        onNodeWithTag("dia-mes-2026-11-03").performClick(); assertEquals(LocalDate(2026, 11, 3), escolhido)
        onNodeWithTag("livre-10").performScrollTo().performClick(); assertEquals(dia to 10, nova)
    }

    @Test fun semPacienteAgendavelOHorarioLivreFicaDesabilitado() = runSkikoComposeUiTest(size = Size(390f, 2400f)) {
        setContent { DietaTheme { AgendaTela(estado.copy(pacientes = listOf(carlos.copy(status = StatusVinculo.ENCERRADO))), AcoesAgenda(), fuso, agora) } }
        onNodeWithTag("livre-10").assertIsNotEnabled()
    }

    @Test fun modoMesApareceNosChips() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        var modo: ModoAgenda? = null
        setContent { DietaTheme { AgendaTela(estado.copy(modo = ModoAgenda.DIA), AcoesAgenda(aoModo = { modo = it }), fuso, agora) } }
        onNodeWithText("Mês").performClick(); assertEquals(ModoAgenda.MES, modo)
    }
}
