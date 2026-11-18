package org.br.dietamais.ui.paciente

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.rotina.ErroForaDoPlano
import org.br.dietamais.dominio.rotina.FormularioForaDoPlano
import org.br.dietamais.ui.paciente.dieta.*
import org.br.dietamais.ui.paciente.hoje.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.uuid.Uuid

/** MOB-88: artboards `P-Refeicao-Humor` e `P-Fora-Plano` nos 3 tamanhos. */
@OptIn(ExperimentalTestApi::class)
class HumorForaDoPlanoTelasTest {
    private val tamanhos = listOf(Size(360f, 800f), Size(768f, 1024f), Size(1280f, 900f))

    private fun estadoComDieta(): EstadoDietaPaciente {
        val d = JsonContrato.decodeFromString<DietasPacienteResposta>(recursoContrato("fase-2/listarMinhasDietas/resposta.json")).itens.single()
        return EstadoDietaPaciente(carregado = true, dieta = d, refeicoes = d.refeicoes, dia = LocalDate(2026, 11, 1), hoje = LocalDate(2026, 11, 1),
            dias = listOf(LocalDate(2026, 11, 1)))
    }

    @Test fun humorMostraAsDuasEscalasEChamaAsAcoes() {
        tamanhos.forEach { tamanho ->
            runSkikoComposeUiTest(size = tamanho) {
                val escolhas = mutableListOf<String>(); var salvou = false; var pulou = false
                setContent { DietaTheme { FolhaHumor(EstadoHumor(Uuid.random(), "Almoço", fome = 3), { escolhas += "fome-$it" }, { escolhas += "depois-$it" }, { salvou = true }, { pulou = true }) } }
                onNodeWithText("Como foi: almoço?").assertExists()
                onNodeWithText("Fome antes de comer").assertExists(); onNodeWithText("Como você ficou depois").assertExists()
                onNodeWithTag("fome-3").assertIsSelected(); onNodeWithTag("fome-2").assertIsNotSelected()
                onNodeWithContentDescription("5 de 5, Cheio demais").assertExists()
                onNodeWithTag("fome-4").performClick(); onNodeWithTag("depois-2").performClick()
                assertEquals(listOf("fome-4", "depois-2"), escolhas)
                onNodeWithText("Salvar").performClick(); assertTrue(salvou)
                onNodeWithText("Pular").performClick(); assertTrue(pulou)
            }
        }
    }

    @Test fun foraDoPlanoMostraCamposErrosERefeicoes() {
        val refeicoes = estadoComDieta().refeicoes
        tamanhos.forEach { tamanho ->
            runSkikoComposeUiTest(size = tamanho) {
                var texto = ""; var refeicao: Uuid? = Uuid.random(); var registrou = false; var cancelou = false
                setContent { DietaTheme { FolhaForaDoPlano(FormularioForaDoPlano("Pão", null, "9:7"), setOf(ErroForaDoPlano.HORA), refeicoes,
                    { texto = it }, { refeicao = it }, {}, { registrou = true }, { cancelou = true }) } }
                onNodeWithText("Comi algo fora do plano").assertExists()
                onNodeWithText("3/280").assertExists(); onNodeWithText("Nenhuma").assertExists()
                onNodeWithText("Informe um horário válido que já passou (HH:MM).").assertExists()
                onNodeWithText("Escreva o que você comeu (até 280 caracteres).").assertDoesNotExist()
                onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("fora-texto"))).performTextInput("!"); assertTrue(texto.isNotEmpty())
                onNodeWithText(refeicoes.first().nome).performClick(); assertEquals(refeicoes.first().id, refeicao)
                onNodeWithText("Registrar").performClick(); assertTrue(registrou)
                onNodeWithText("Cancelar").performClick(); assertTrue(cancelou)
            }
        }
    }

    @Test fun hojeTemBotaoForaDoPlanoEAvisoDeDesfazer() = runSkikoComposeUiTest(size = Size(390f, 1600f)) {
        var abriu = false; var desfez = false
        setContent { DietaTheme { HojeTela(estadoComDieta().copy(podeDesfazerFora = true),
            AcoesHoje({}, { _, _ -> }, {}, {}, aoAbrirFora = { abriu = true }, aoDesfazerFora = { desfez = true })) } }
        onNodeWithText("Comi algo fora do plano").performScrollTo().performClick(); assertTrue(abriu)
        onNodeWithText("Registro fora do plano salvo").assertExists()
        onNodeWithText("Desfazer").performClick(); assertTrue(desfez)
    }
}
