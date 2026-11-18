package org.br.dietamais.ui.nutri

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.window.core.layout.WindowSizeClass
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.dieta.AlertaItem
import org.br.dietamais.ui.nutri.dieta.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-83: artboards `A-Montador-Alerta` e `A-Montador-Confirmar` nos três tamanhos. */
@OptIn(ExperimentalTestApi::class)
class AlertaAlergiaMontadorTest {
    private val agora = Instant.parse("2026-11-17T12:00:00Z")
    private val grupo = Uuid.parse("019a0f32-5000-7000-8000-000000000001")
    private val pasta = AlimentoResposta(Uuid.parse("019a0f32-5000-7000-8000-000000000002"), null, grupo, "Pasta de amendoim", "1 colher de sopa", 15.0, true, 1, agora, listOf("AMENDOIM"))
    private val iogurte = AlimentoResposta(Uuid.parse("019a0f32-5000-7000-8000-000000000003"), null, grupo, "Iogurte natural", "1 pote", 170.0, true, 1, agora, listOf("CONTEM_LACTOSE"))
    private val alergia = RestricaoSaudeDto(Uuid.parse("019b0000-0000-7000-8000-000000000002"), TipoRestricaoSaude.ALERGIA, "amendoim", GravidadeRestricao.GRAVE, marcadores = listOf("AMENDOIM"))
    private val intolerancia = RestricaoSaudeDto(Uuid.parse("019b0000-0000-7000-8000-000000000001"), TipoRestricaoSaude.INTOLERANCIA, "lactose", GravidadeRestricao.MODERADA, marcadores = listOf("CONTEM_LACTOSE"))
    private val itemPasta = ItemEdicao(alimentoId = pasta.id); private val itemIogurte = ItemEdicao(alimentoId = iogurte.id)
    private val refeicao = RefeicaoEdicao(nome = "Lanche da tarde", horario = "16:00", itens = listOf(itemPasta, itemIogurte))

    private fun estado(confirmada: Boolean = false, revisar: Boolean = false): EstadoDieta {
        val ref = if (confirmada) refeicao.copy(itens = listOf(itemPasta.copy(alergiaConfirmada = true), itemIogurte)) else refeicao
        return EstadoDieta(rascunho = RascunhoDieta(titulo = "Plano", versao = 2, refeicoes = listOf(ref)), alimentos = listOf(iogurte, pasta),
            restricoes = listOf(alergia, intolerancia),
            alertas = mapOf(itemPasta.chave to listOf(AlertaItem(alergia.id, alergia.tipo, alergia.nome, "AMENDOIM", GravidadeRestricao.GRAVE)),
                itemIogurte.chave to listOf(AlertaItem(intolerancia.id, intolerancia.tipo, intolerancia.nome, "CONTEM_LACTOSE", GravidadeRestricao.MODERADA))),
            revisarAlergia = if (revisar) refeicao.chave to itemPasta.chave else null)
    }

    private fun conferirAlerta(largura: Int, altura: Int) = runSkikoComposeUiTest(size = Size(largura.toFloat(), altura.toFloat())) {
        setContent { DietaTheme { MontarDietaTela(estado(), AcoesMontador(), WindowSizeClass(largura, altura)) } }
        onNodeWithTag("restricoes-ficha").assertExists()
        onNodeWithText("Restrições da ficha: alergia a amendoim (grave), intolerância a lactose (moderada).").assertExists()
        onNodeWithText("Alergia grave na ficha: amendoim. Confirme para poder publicar.").assertExists()
        onNodeWithText("Intolerância moderada na ficha: lactose. Só um aviso.").assertExists()
        onNodeWithTag("alergias-pendentes").assertExists()
        onNodeWithText("Publicar").assertIsNotEnabled()
    }

    @Test fun celular() = conferirAlerta(390, 844)
    @Test fun tabletRetrato() = conferirAlerta(700, 1000)
    @Test fun tablet() = conferirAlerta(1180, 820)

    @Test fun confirmadaMostraDesfazerELiberaPublicar() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        setContent { DietaTheme { MontarDietaTela(estado(confirmada = true), AcoesMontador(), WindowSizeClass(390, 844)) } }
        onNodeWithText("Alergia na ficha: amendoim. Você confirmou manter este alimento.").assertExists()
        onNodeWithText("Desfazer confirmação").assertExists()
        onNodeWithTag("alergias-pendentes").assertDoesNotExist()
        onNodeWithText("Publicar").assertIsEnabled()
    }

    @Test fun folhaDeConfirmacaoChamaManterETrocar() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        var manteve = 0; var trocou: Pair<Uuid, Uuid>? = null
        setContent { DietaTheme { MontarDietaTela(estado(revisar = true), AcoesMontador(aoConfirmarAlergia = { manteve++ }, aoTrocarAlimento = { r, i -> trocou = r to i }), WindowSizeClass(390, 844)) } }
        onNodeWithText("Manter um alimento com alergia?").assertIsDisplayed()
        onNodeWithText("Manter e confirmar").performClick(); assertEquals(1, manteve)
        onNodeWithTag("alergia-trocar-folha").performClick(); assertEquals(refeicao.chave to itemPasta.chave, trocou)
    }
}
