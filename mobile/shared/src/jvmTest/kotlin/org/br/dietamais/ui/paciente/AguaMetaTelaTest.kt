package org.br.dietamais.ui.paciente

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.metas.progressoMeta
import org.br.dietamais.ui.paciente.dieta.*
import org.br.dietamais.ui.paciente.hoje.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-99: o cartão de água do Hoje com a sequência e a semana da meta de água (artboard `P-Hoje-Metas`), nos 3 tamanhos. */
@OptIn(ExperimentalTestApi::class)
class AguaMetaTelaTest {
    private val tamanhos = listOf(Size(360f, 2400f), Size(768f, 2400f), Size(1280f, 2400f))
    private val hoje = LocalDate(2026, 11, 18) // quarta

    private fun estado(comMeta: Boolean): EstadoDietaPaciente {
        val d = JsonContrato.decodeFromString<DietasPacienteResposta>(recursoContrato("fase-2/listarMinhasDietas/resposta.json")).itens.single()
        val criada = Instant.parse("2026-11-01T00:00:00Z")
        val meta = MetaResposta(Uuid.random(), Uuid.random(), TipoMeta.AGUA, null, 2000.0, null, FrequenciaMeta.DIARIA, versao = 1, criadoEm = criada, atualizadoEm = criada)
        val registros = listOf(LocalDate(2026, 11, 16), LocalDate(2026, 11, 17)).map { RegistroMetaResposta(Uuid.random(), meta.id, it, 2000.0, criada) }
        return EstadoDietaPaciente(carregado = true, dieta = d, refeicoes = d.refeicoes, dia = hoje, hoje = hoje, dias = listOf(hoje),
            aguaMl = 500, metaAguaMl = 2000, progressoAgua = if (comMeta) progressoMeta(meta, registros, hoje) else null)
    }

    @Test fun cartaoDeAguaMostraASequenciaEOsDiasDaSemana() {
        tamanhos.forEach { tamanho ->
            runSkikoComposeUiTest(size = tamanho) {
                var bebeu = 0
                setContent { DietaTheme { HojeTela(estado(true), AcoesHoje({}, { _, _ -> }, {}, { bebeu = it })) } }
                onNodeWithText("2 dias seguidos").performScrollTo().assertIsDisplayed()
                onNodeWithTag("agua-semana").assertTextEquals("2 de 3 até agora")
                onNodeWithText("+ 300 ml").performScrollTo().performClick(); assertEquals(300, bebeu)
            }
        }
    }

    @Test fun semAMetaOCartaoFicaComoAntes() = runSkikoComposeUiTest(size = Size(390f, 2400f)) {
        setContent { DietaTheme { HojeTela(estado(false), AcoesHoje({}, { _, _ -> }, {}, {})) } }
        onNodeWithTag("agua-semana").assertDoesNotExist()
        onNodeWithText("2 dias seguidos").assertDoesNotExist()
    }
}
