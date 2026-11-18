package org.br.dietamais.ui.nutri

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.ui.nutri.paciente.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-89: artboard `A-Adesao-Humor` nos 3 tamanhos. */
@OptIn(ExperimentalTestApi::class)
class AdesaoHumorCardTest {
    private val fuso = TimeZone.of("America/Sao_Paulo")
    private val agora = Instant.parse("2026-11-16T15:00:00Z")
    private val tamanhos = listOf(Size(360f, 1200f), Size(768f, 1200f), Size(1280f, 1200f))
    private val adesao = AdesaoDto(Uuid.random(), JanelaAdesaoDto(7, 42, 25, 4, 2, 69.0), JanelaAdesaoDto(30, 180, 100, 10, 10, 61.1),
        HumorAdesaoDto(3.8, 2.7, 12), listOf(
            RegistroForaDoPlanoResposta(Uuid.random(), LocalDate(2026, 11, 10), "Hambúrguer com batata frita", null, Instant.parse("2026-11-10T16:00:00Z")),
            RegistroForaDoPlanoResposta(Uuid.random(), LocalDate(2026, 11, 14), "2 fatias de bolo de chocolate", Uuid.random(), Instant.parse("2026-11-14T18:30:00Z"))))

    @Test fun mostraRegistrosMediasEForaDoPlano() {
        tamanhos.forEach { tamanho ->
            runSkikoComposeUiTest(size = tamanho) {
                setContent { DietaTheme { AdesaoHumorCard(EstadoAdesao(adesao = adesao), {}, fuso = fuso, agora = agora) } }
                onNodeWithText("Adesão · últimos 7 dias").assertExists()
                onNodeWithText("31 de 42").assertExists()
                onNodeWithText("10/11 a 16/11 · 12 respostas").assertExists()
                onNodeWithText("3,8 de 5 · bastante").assertExists(); onNodeWithText("2,7 de 5 · satisfeito").assertExists()
                onNodeWithText("2 na semana").assertExists()
                onNodeWithText("sáb 14/11, 15:30").assertExists(); onNodeWithText("ter 10/11, 13:00").assertExists()
                val ordem = onAllNodes(hasText("Hambúrguer com batata frita") or hasText("2 fatias de bolo de chocolate")).fetchSemanticsNodes()
                assertEquals(2, ordem.size)
            }
        }
    }

    @Test fun semRespostasESemRegistrosMostraOsVazios() = runSkikoComposeUiTest(size = Size(390f, 900f)) {
        setContent { DietaTheme { AdesaoHumorCard(EstadoAdesao(adesao = adesao.copy(humor = null, foraDoPlano = null)), {}, fuso = fuso, agora = agora) } }
        onNodeWithText("O paciente ainda não respondeu fome e sensação nesta semana.").assertExists()
        onNodeWithText("Nenhum registro fora do plano nesta semana.").assertExists()
        onNodeWithText("Fome antes").assertDoesNotExist()
    }

    @Test fun erroOfereceTentarDeNovo() = runSkikoComposeUiTest(size = Size(390f, 600f)) {
        var tentou = false
        setContent { DietaTheme { AdesaoHumorCard(EstadoAdesao(erro = MensagemAdesao.ERRO), { tentou = true }) } }
        onNodeWithText("Não foi possível carregar a adesão.").assertExists()
        onNodeWithText("Tentar novamente").performClick(); assertTrue(tentou)
    }

    @Test fun respostaDoBackendDecodificaComEOSemOsCamposNovos() {
        val base = """{"pacienteId":"019a0f32-5000-7000-8000-000000000003","dias7":{"dias":7,"esperadas":42,"feitas":25,"trocadas":4,"puladas":2,"percentual":69.05},
            "dias30":{"dias":30,"esperadas":0,"feitas":0,"trocadas":0,"puladas":0,"percentual":null}"""
        val antiga = org.br.dietamais.data.remoto.JsonContrato.decodeFromString<AdesaoDto>("$base}")
        assertNull(antiga.humor); assertNull(antiga.foraDoPlano)
        val nova = org.br.dietamais.data.remoto.JsonContrato.decodeFromString<AdesaoDto>(base + ""","humor":{"mediaFomeAntes":3.5,"mediaSensacaoDepois":null,"registros":2},
            "foraDoPlano":[{"id":"019a0f32-5000-7000-8000-000000000009","data":"2026-11-14","texto":"Bolo","refeicaoId":null,"registradoEm":"2026-11-14T18:30:00Z"}],"novo":1}""")
        assertEquals(2, nova.humor?.registros); assertEquals("Bolo", nova.foraDoPlano?.single()?.texto)
    }
}
