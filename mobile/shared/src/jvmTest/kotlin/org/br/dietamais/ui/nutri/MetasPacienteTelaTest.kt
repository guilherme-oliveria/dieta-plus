package org.br.dietamais.ui.nutri

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.metas.ErroMeta
import org.br.dietamais.dominio.metas.FormularioMeta
import org.br.dietamais.ui.nutri.metas.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-91: artboards `A-Metas-Paciente`, `A-Meta-Nova` e `A-Modelos-Meta` nos 3 tamanhos. */
@OptIn(ExperimentalTestApi::class)
class MetasPacienteTelaTest {
    private val tamanhos = listOf(Size(360f, 1400f), Size(768f, 1400f), Size(1280f, 1400f))
    private val t = Instant.parse("2026-11-01T10:00:00Z")
    private val p = Uuid.random()
    private val agua = MetaResposta(Uuid.random(), p, TipoMeta.AGUA, null, 2100.0, null, FrequenciaMeta.DIARIA, alvoPadrao = true, versao = 1, criadoEm = t, atualizadoEm = t)
    private val caminhada = MetaResposta(Uuid.random(), p, TipoMeta.ATIVIDADE_MIN, null, 30.0, null, FrequenciaMeta.SEMANAL, 3, versao = 1, criadoEm = t, atualizadoEm = t)
    private val frutas = MetaResposta(Uuid.random(), p, TipoMeta.FRUTAS_VERDURAS, null, 5.0, null, FrequenciaMeta.DIARIA, versao = 1, criadoEm = t, atualizadoEm = t)
    private val refri = MetaResposta(Uuid.random(), p, TipoMeta.SIM_NAO, "Sem refrigerante", 1.0, null, FrequenciaMeta.DIARIA, versao = 1, criadoEm = t, atualizadoEm = t)
    private val estado = EstadoMetasNutri(p, carregado = true, metas = listOf(agua, caminhada, frutas, refri), hoje = LocalDate(2026, 11, 16),
        cumprimento = CumprimentoMetasResposta(LocalDate(2026, 11, 10), LocalDate(2026, 11, 16), listOf(
            CumprimentoMeta(agua.id, TipoMeta.AGUA, null, 7, 6, 92.0, 4, 6), CumprimentoMeta(caminhada.id, TipoMeta.ATIVIDADE_MIN, null, 3, 2, 66.7, 0, 2),
            CumprimentoMeta(frutas.id, TipoMeta.FRUTAS_VERDURAS, null, 7, 3, 42.9, 0, 2), CumprimentoMeta(refri.id, TipoMeta.SIM_NAO, "Sem refrigerante", 7, 6, 85.7, 2, 5))))

    @Test fun listaMetasComCumprimentoEAlerta() {
        tamanhos.forEach { tamanho -> runSkikoComposeUiTest(size = tamanho) {
            var editou: Uuid? = null
            setContent { DietaTheme { MetasPacienteTela(estado, AcoesMetas(aoEditar = { editou = it })) } }
            onNodeWithText("Cumprimento na semana").assertExists(); onNodeWithText("10/11 a 16/11").assertExists()
            onNodeWithText("2,1 L, todo dia · padrão 35 ml/kg").assertExists(); onNodeWithText("92%").assertExists()
            onNodeWithText("Alvo padrão pelo peso. Você pode ajustar na meta.").assertExists()
            onNodeWithText("30 min, 3 vezes por semana").assertExists(); onNodeWithText("67%").assertExists()
            onNodeWithText("5 porções, todo dia").assertExists(); onNodeWithText("43%").assertExists()
            onAllNodesWithText("Abaixo de 50% na semana.").assertCountEquals(1)
            onNodeWithText("Sem refrigerante").assertExists(); onNodeWithText("Sim ou não, todo dia").assertExists()
            onNodeWithContentDescription("Editar meta Sem refrigerante").performClick(); assertEquals(refri.id, editou)
        } }
    }

    @Test fun formularioNovoMostraTiposFrequenciaEErros() {
        tamanhos.forEach { tamanho -> runSkikoComposeUiTest(size = tamanho) {
            val chamadas = mutableListOf<String>()
            val e = estado.copy(formulario = FormularioMeta(TipoMeta.CONTADOR, frequencia = FrequenciaMeta.SEMANAL, vezesPorSemana = 3, inicio = "17/11/2026"),
                errosFormulario = setOf(ErroMeta.NOME, ErroMeta.ALVO))
            setContent { DietaTheme { FormularioMetaConteudo(e, "Ana", AcoesMetas(aoTipo = { chamadas += "tipo-$it" }, aoVezes = { chamadas += "vezes$it" }, aoSalvar = { chamadas += "salvar" })) } }
            onNodeWithText("Nova meta para Ana").assertExists()
            onNodeWithText("Água").assertDoesNotExist()
            onNodeWithText("Passos").performClick()
            onNodeWithText("3 vezes por semana").assertExists()
            onNodeWithContentDescription("Uma vez a mais").performClick()
            onNodeWithText("Dê um nome à meta (até 80 caracteres).").assertExists(); onNodeWithText("Informe um alvo maior que zero.").assertExists()
            onNodeWithText("Encerrar meta").assertDoesNotExist()
            onNodeWithText("Salvar meta").performClick()
            assertEquals(listOf("tipo-PASSOS", "vezes1", "salvar"), chamadas)
        } }
    }

    @Test fun editarAguaSoMostraOAlvo() = runSkikoComposeUiTest(size = Size(390f, 1000f)) {
        val e = estado.copy(formulario = FormularioMeta(TipoMeta.AGUA, alvo = "2100", metaId = agua.id, versao = 1))
        setContent { DietaTheme { FormularioMetaConteudo(e, "Ana", AcoesMetas()) } }
        onNodeWithText("Editar meta").assertExists(); onNodeWithTag("meta-alvo").assertExists()
        onNodeWithText("Frequência").assertDoesNotExist(); onNodeWithText("Encerrar meta").assertDoesNotExist()
        onNodeWithText("A meta de água vem com 35 ml por kg do peso mais recente. Ao mudar o alvo, ele deixa de seguir o peso.").assertExists()
    }

    @Test fun modelosAplicarESalvarComoModelo() {
        val modelo = ModeloMetaResposta(Uuid.random(), "Começo de reeducação", listOf(ItemModeloMeta(TipoMeta.FRUTAS_VERDURAS, null, 5.0, null, FrequenciaMeta.DIARIA)), 1, t, t)
        tamanhos.forEach { tamanho -> runSkikoComposeUiTest(size = tamanho) {
            var aplicado: Uuid? = null; var novo = false
            setContent { DietaTheme { ModelosMetaConteudo(estado.copy(modelosAbertos = true, modelos = listOf(modelo)), "Ana",
                AcoesMetas(aoAplicar = { aplicado = it }, aoNovoModelo = { novo = true })) } }
            onNodeWithText("Aplicar em Ana").assertExists(); onNodeWithText("Começo de reeducação").assertExists()
            onNodeWithText("Frutas e verduras 5 porções, todo dia").assertExists()
            onNodeWithContentDescription("Aplicar o modelo Começo de reeducação").performClick(); assertEquals(modelo.id, aplicado)
            onNodeWithText("Salvar as metas deste paciente como modelo").performClick(); assertTrue(novo)
        } }
    }
}
