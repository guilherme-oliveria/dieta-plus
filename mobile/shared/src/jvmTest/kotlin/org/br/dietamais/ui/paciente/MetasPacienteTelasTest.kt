package org.br.dietamais.ui.paciente

import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.metas.progressoMeta
import org.br.dietamais.ui.paciente.metas.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

@androidx.compose.runtime.Composable
private fun Rolavel(conteudo: @androidx.compose.runtime.Composable () -> Unit) =
    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { conteudo() }

/** MOB-90: artboards `P-Hoje-Metas` e `P-Metas-Semana` nos 3 tamanhos. */
@OptIn(ExperimentalTestApi::class)
class MetasPacienteTelasTest {
    private val tamanhos = listOf(Size(360f, 800f), Size(768f, 1024f), Size(1280f, 900f))
    private val hoje = LocalDate(2026, 11, 17)
    private val agora = Instant.parse("2026-11-17T12:00:00Z")
    private fun meta(tipo: TipoMeta, alvo: Double, nome: String? = null, frequencia: FrequenciaMeta = FrequenciaMeta.DIARIA, vezes: Int? = null) =
        MetaResposta(Uuid.random(), Uuid.random(), tipo, nome, alvo, null, frequencia, vezes, versao = 1, criadoEm = agora, atualizadoEm = agora)
    private val caminhada = meta(TipoMeta.ATIVIDADE_MIN, 30.0, frequencia = FrequenciaMeta.SEMANAL, vezes = 3)
    private val frutas = meta(TipoMeta.FRUTAS_VERDURAS, 5.0)
    private val semRefri = meta(TipoMeta.SIM_NAO, 1.0, nome = "Sem refrigerante")
    private fun reg(m: MetaResposta, dia: Int, valor: Double) = RegistroMetaResposta(Uuid.random(), m.id, LocalDate(2026, 11, dia), valor, agora)
    private val registros = listOf(reg(caminhada, 16, 30.0), reg(caminhada, 17, 20.0), reg(frutas, 15, 5.0), reg(frutas, 16, 5.0), reg(frutas, 17, 3.0))

    private fun estado(outro: Uuid? = null, erroOutro: Boolean = false): EstadoMetasPaciente {
        val ps = listOf(caminhada, frutas, semRefri).map { progressoMeta(it, registros, hoje) }
        return EstadoMetasPaciente(carregado = true, hojeData = hoje, hoje = ps, semana = ps, outroValor = outro, textoOutro = "", erroOutro = erroOutro)
    }

    @Test fun hojeMostraAsMetasComSequenciaEBotoes() {
        tamanhos.forEach { tamanho ->
            runSkikoComposeUiTest(size = tamanho) {
                val somas = mutableListOf<Pair<Uuid, Double>>(); var cumpri: Uuid? = null; var semana = false
                setContent { DietaTheme { Rolavel { MetasHoje(estado(), AcoesMetasPaciente(aoSomar = { id, v -> somas += id to v }, aoCumpri = { cumpri = it }, aoAbrirSemana = { semana = true })) } } }
                onNodeWithText("Metas de hoje").assertExists()
                onNodeWithText("Frutas e verduras").assertExists()
                onNodeWithText("3 porções").assertExists()
                onNodeWithText("de 5 porções · hoje").assertExists()
                onNodeWithText("2 dias seguidos").assertExists()
                onNodeWithText("1 de 3 na semana · faltam 2").assertExists()
                onNodeWithText("+ 1 porção").performScrollTo().performClick()
                onNodeWithText("+ 10 min").performScrollTo().performClick()
                assertEquals(listOf(frutas.id to 1.0, caminhada.id to 10.0), somas)
                onNodeWithText("Cumpri hoje").performScrollTo().performClick(); assertEquals(semRefri.id, cumpri)
                onNodeWithText("Ver semana").performClick(); assertTrue(semana)
            }
        }
    }

    @Test fun outroValorMostraOCampoEOErro() = runSkikoComposeUiTest(size = Size(360f, 800f)) {
        var confirmou = false
        setContent { DietaTheme { Rolavel { MetasHoje(estado(outro = frutas.id, erroOutro = true), AcoesMetasPaciente(aoConfirmarOutro = { confirmou = true })) } } }
        onNodeWithText("Informe um valor maior que zero.").assertExists()
        onNodeWithText("Adicionar").performScrollTo().performClick(); assertTrue(confirmou)
    }

    @Test fun semMetasNaoMostraNada() = runSkikoComposeUiTest(size = Size(360f, 800f)) {
        setContent { DietaTheme { Rolavel { MetasHoje(EstadoMetasPaciente(carregado = true, hojeData = hoje), AcoesMetasPaciente()) } } }
        onNodeWithText("Metas de hoje").assertDoesNotExist()
    }

    @Test fun semanaMostraOsDiasEOProgresso() {
        tamanhos.forEach { tamanho ->
            runSkikoComposeUiTest(size = tamanho) {
                var voltou = false
                setContent { DietaTheme { MetasSemanaTela(estado(), AcoesMetasPaciente(aoFecharSemana = { voltou = true })) } }
                onNodeWithText("Minhas metas").assertExists()
                onNodeWithText("Semana de 16/11 a 22/11 · hoje é terça-feira").assertExists()
                onAllNodesWithContentDescription("segunda-feira 16/11: cumprida").onFirst().assertExists()
                onNodeWithText("1 de 2 até agora").assertExists()
                onNodeWithText("0 de 2 até agora").assertExists()
                onNodeWithText("Cada bolinha é um dia: cheia quando você cumpriu a meta.").performScrollTo().assertExists()
                onNodeWithContentDescription("Voltar").performClick(); assertTrue(voltou)
            }
        }
    }
}
