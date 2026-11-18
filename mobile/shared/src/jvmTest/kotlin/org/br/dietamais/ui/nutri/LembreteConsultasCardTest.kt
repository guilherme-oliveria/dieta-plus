package org.br.dietamais.ui.nutri

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import org.br.dietamais.data.remoto.dto.AntecedenciaLembrete
import org.br.dietamais.ui.nutri.assinatura.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*

/** MOB-86, artboard `A-Conta-Lembrete` (DES-17), nos três tamanhos. */
@OptIn(ExperimentalTestApi::class)
class LembreteConsultasCardTest {
    private val tamanhos = listOf(Size(390f, 844f), Size(834f, 1112f), Size(1280f, 900f))

    @Test fun mostraAsOpcoesEEscolheNosTresTamanhos() = tamanhos.forEach { tamanho ->
        runSkikoComposeUiTest(size = tamanho) {
            var antecedencia: AntecedenciaLembrete? = null; var extra: Boolean? = null
            setContent { DietaTheme { LembreteConsultasCard(EstadoLembreteConsultas(carregado = true), AcoesLembreteConsultas(aoAntecedencia = { antecedencia = it }, aoExtraUmaHora = { extra = it })) } }
            onNodeWithText("Lembrete das consultas").assertExists()
            onNodeWithText("Padrão: 1 dia antes. Vale para você e para seus pacientes.").assertExists()
            onNodeWithTag("lembrete-UM_DIA").assertIsSelected()
            onNodeWithText("Salvar").assertIsNotEnabled()
            onNodeWithTag("lembrete-DUAS_HORAS").performClick(); assertEquals(AntecedenciaLembrete.DUAS_HORAS, antecedencia)
            onNodeWithTag("lembrete-extra").assertIsOff().performClick(); assertEquals(true, extra)
        }
    }

    @Test fun alteradoHabilitaSalvarEMostraOAviso() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        setContent { DietaTheme { LembreteConsultasCard(EstadoLembreteConsultas(carregado = true, alterado = true, extraUmaHora = true, aviso = MensagemLembrete.SALVO), AcoesLembreteConsultas()) } }
        onNodeWithText("Salvar").assertIsEnabled()
        onNodeWithTag("lembrete-extra").assertIsOn()
        onNodeWithText("Lembrete das consultas salvo.").assertExists()
    }

    @Test fun semRedeDesabilitaTudo() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        setContent { DietaTheme { LembreteConsultasCard(EstadoLembreteConsultas(carregado = true, alterado = true, semConexao = true, erro = MensagemLembrete.SEM_CONEXAO), AcoesLembreteConsultas()) } }
        onNodeWithText("Salvar").assertIsNotEnabled()
        onNodeWithTag("lembrete-DOIS_DIAS").assertIsNotEnabled()
        onNodeWithText("Sem conexão. O lembrete é salvo só com internet.").assertExists()
    }

    @Test fun semLeituraPedeParaTentarDeNovo() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        var recarregou = false
        setContent { DietaTheme { LembreteConsultasCard(EstadoLembreteConsultas(carregado = false, erro = MensagemLembrete.ERRO), AcoesLembreteConsultas(aoRecarregar = { recarregou = true })) } }
        onNodeWithText("Tentar de novo").performClick(); assertTrue(recarregou)
    }
}
