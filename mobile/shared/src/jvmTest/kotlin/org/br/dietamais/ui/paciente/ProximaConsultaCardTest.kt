package org.br.dietamais.ui.paciente

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.ui.nutri.agenda.agoraFalso
import org.br.dietamais.ui.nutri.agenda.consultaFalsa
import org.br.dietamais.ui.paciente.hoje.ProximaConsultaCard
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/** MOB-87 (artboard `P-Proxima-Consulta`, D-03): o paciente vê modalidade, "Entrar na consulta" e a orientação; nunca a observação interna. */
@OptIn(ExperimentalTestApi::class)
class ProximaConsultaCardTest {
    private val fuso = TimeZone.of("America/Sao_Paulo")
    private val online = consultaFalsa(Uuid.random(), agoraFalso + 6.hours, 50, TipoConsulta.RETORNO).copy(modalidade = ModalidadeConsulta.ONLINE,
        linkOnline = "https://meet.exemplo.invalid/abc", orientacaoPaciente = "Venha em jejum de 3 h.", observacaoInterna = "Nota só da nutri")

    private fun onlineMostraBotaoEOrientacao(size: Size) = runSkikoComposeUiTest(size = size) {
        var aberto: String? = null
        setContent { DietaTheme { ProximaConsultaCard(listOf(online), agoraFalso, fuso, aoEntrar = { aberto = it }) } }
        onNodeWithText("Hoje, 15:00").assertIsDisplayed(); onNodeWithText("Retorno · 50 min").assertIsDisplayed(); onNodeWithText("Online").assertIsDisplayed()
        onNodeWithText("Orientação da nutri").assertIsDisplayed(); onNodeWithText("Venha em jejum de 3 h.").assertIsDisplayed()
        onNodeWithText("Nota só da nutri").assertDoesNotExist(); onNodeWithText("Faltou").assertDoesNotExist()
        onNodeWithText("Entrar na consulta").performClick()
        assertEquals("https://meet.exemplo.invalid/abc", aberto)
    }

    @Test fun celular() = onlineMostraBotaoEOrientacao(Size(390f, 844f))
    @Test fun tablet() = onlineMostraBotaoEOrientacao(Size(834f, 1112f))
    @Test fun desktop() = onlineMostraBotaoEOrientacao(Size(1280f, 900f))

    @Test fun presencialMostraLocalSemBotao() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        val presencial = consultaFalsa(Uuid.random(), agoraFalso + 2.days, 60, TipoConsulta.PRIMEIRA, lugar = "Rua Fictícia, 10").copy(modalidade = ModalidadeConsulta.PRESENCIAL)
        setContent { DietaTheme { ProximaConsultaCard(listOf(presencial), agoraFalso, fuso) } }
        onNodeWithText("30/10/2026, 09:00").assertIsDisplayed(); onNodeWithText("Presencial").assertIsDisplayed(); onNodeWithText("Rua Fictícia, 10").assertIsDisplayed()
        onNodeWithText("Entrar na consulta").assertDoesNotExist(); onNodeWithText("Orientação da nutri").assertDoesNotExist()
    }

    @Test fun amanhaELinkSemHttpsNaoViraBotao() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        val inseguro = online.copy(inicio = agoraFalso + 1.days, linkOnline = "http://meet.exemplo.invalid/abc", orientacaoPaciente = null)
        setContent { DietaTheme { ProximaConsultaCard(listOf(inseguro), agoraFalso, fuso) } }
        onNodeWithText("Amanhã, 09:00").assertIsDisplayed(); onNodeWithText("Entrar na consulta").assertDoesNotExist()
    }
}
