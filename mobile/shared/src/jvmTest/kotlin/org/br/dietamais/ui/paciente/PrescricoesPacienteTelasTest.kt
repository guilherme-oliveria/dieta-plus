package org.br.dietamais.ui.paciente

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.OrientacaoResposta
import org.br.dietamais.data.remoto.dto.SuplementoResposta
import org.br.dietamais.dominio.prescricoes.SecaoPrescricaoPaciente
import org.br.dietamais.ui.paciente.dieta.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class PrescricoesPacienteTelasTest {
    private val t = Instant.parse("2026-11-01T00:00:00Z")
    private val agora = Instant.parse("2026-11-18T12:00:00Z")
    private val vitamina = SuplementoResposta(Uuid.random(), Uuid.random(), nome = "Vitamina D3", dose = "1 cápsula", forma = "Cápsula", frequencia = "Todo dia",
        horarios = listOf("08:00"), inicio = LocalDate(2026, 11, 1), fim = LocalDate(2027, 2, 17), versao = 1, criadoEm = t, atualizadoEm = t)
    private val creatina = SuplementoResposta(Uuid.random(), Uuid.random(), nome = "Creatina", dose = "3 g", frequencia = "Todo dia", horarios = listOf("16:00"),
        inicio = LocalDate(2026, 11, 1), versao = 1, criadoEm = t, atualizadoEm = t)
    private val prato = OrientacaoResposta(Uuid.random(), Uuid.random(), "Como montar o prato", "Para o almoço:\n- **Metade** com verduras", null, 1,
        Instant.parse("2026-11-18T10:12:00Z"), t)
    private val agua = OrientacaoResposta(Uuid.random(), Uuid.random(), "Hidratação no calor", "Beba **2 L** de água", null, 1, Instant.parse("2026-11-03T10:00:00Z"), t)
    private val tamanhos = listOf(Size(360f, 800f), Size(768f, 1024f), Size(1280f, 900f))

    @Test fun suplementosNosTresTamanhosComProximoEDesligarLembrete() {
        tamanhos.forEach { tamanho ->
            runSkikoComposeUiTest(size = tamanho) {
                var lembrete: Pair<Uuid, Boolean>? = null
                var voltou = 0
                val estado = EstadoPrescricoesPaciente(carregado = true, suplementos = listOf(vitamina, creatina), agora = agora,
                    proximas = mapOf(creatina.id to Instant.parse("2026-11-18T16:00:00Z"), vitamina.id to Instant.parse("2026-11-19T08:00:00Z")),
                    semLembrete = setOf(vitamina.id), aberta = SecaoPrescricaoPaciente.SUPLEMENTOS)
                setContent { DietaTheme { SuplementosPacienteTela(estado, AcoesPrescricoesPaciente(aoFechar = { voltou++ }, aoLembrete = { id, l -> lembrete = id to l }),
                    "Nut. Luciana", fuso = TimeZone.UTC) } }
                onNodeWithText("2 em uso").assertExists()
                onNodeWithText("1 cápsula · Cápsula").assertExists()
                onNodeWithText("Todo dia · 08:00").assertExists()
                onNodeWithText("Até 17/02/2027").assertExists()
                onNodeWithText("Próximo: hoje às 16:00").assertExists()
                onNodeWithText("Próximo: amanhã às 08:00").assertExists()
                onNodeWithText("Prescrito por Nut. Luciana. Disponível sem internet. Dúvidas sobre a dose, fale com ela.").assertExists()
                onAllNodesWithText("Lembrar no horário")[1].performClick()
                assertEquals(creatina.id to false, lembrete)
                onNodeWithContentDescription("Voltar para a dieta").performClick()
                assertEquals(1, voltou)
            }
        }
    }

    @Test fun orientacoesMostramANovaAbertaEAsAnterioresAoTocar() {
        tamanhos.forEach { tamanho ->
            runSkikoComposeUiTest(size = tamanho) {
                var aberta: Uuid? = null
                val estado = EstadoPrescricoesPaciente(carregado = true, orientacoes = listOf(prato, agua), destacadas = setOf(prato.id), agora = agora,
                    aberta = SecaoPrescricaoPaciente.ORIENTACOES)
                setContent { DietaTheme { OrientacoesPacienteTela(estado, AcoesPrescricoesPaciente(aoOrientacao = { aberta = it }), "Nut. Luciana", fuso = TimeZone.UTC) } }
                onNodeWithText("De Nut. Luciana").assertExists()
                onNodeWithText("Nova").assertExists()
                onNodeWithText("hoje, 10:12").assertExists()
                onNodeWithText("Metade com verduras").assertExists()
                onNodeWithText("Anteriores").assertExists()
                onNodeWithText("03/11/2026").assertExists()
                onNodeWithText("Beba 2 L de água").assertDoesNotExist()
                onNodeWithText("Ler").performClick()
                assertEquals(agua.id, aberta)
            }
        }
    }

    @Test fun anteriorAbertaMostraOTextoERecolher() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        setContent { DietaTheme { OrientacoesPacienteTela(EstadoPrescricoesPaciente(carregado = true, orientacoes = listOf(prato, agua), orientacaoAberta = agua.id,
            agora = agora, aberta = SecaoPrescricaoPaciente.ORIENTACOES), AcoesPrescricoesPaciente(), null, fuso = TimeZone.UTC) } }
        onNodeWithText("Beba 2 L de água").assertExists()
        onNodeWithText("Recolher").assertExists()
        onNodeWithText("Nova").assertDoesNotExist()
    }

    @Test fun resumoNaDietaContaNovasESomeSemNada() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        var secao: SecaoPrescricaoPaciente? = null
        setContent { DietaTheme { androidx.compose.foundation.layout.Column {
            PrescricoesResumoCard(EstadoPrescricoesPaciente(carregado = true, suplementos = listOf(vitamina, creatina), orientacoes = listOf(prato, agua), novas = setOf(prato.id)),
                AcoesPrescricoesPaciente(aoAbrir = { secao = it }))
            PrescricoesResumoCard(EstadoPrescricoesPaciente(carregado = true), AcoesPrescricoesPaciente())
        } } }
        onNodeWithText("Suplementos · 2 em uso").assertExists()
        onNodeWithText("Orientações · 1 nova").performClick()
        assertEquals(SecaoPrescricaoPaciente.ORIENTACOES, secao)
        onAllNodesWithTag("prescricoes-resumo").assertCountEquals(1)
    }
}
