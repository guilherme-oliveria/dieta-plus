package org.br.dietamais.ui.nutri

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.ui.nutri.assinatura.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.uuid.Uuid

/** MOB-96, artboards `A-Assinatura-Planos`, `A-Assinatura-Ativa` e `A-Assinatura-Sem-Preco` (DES-22), nos três tamanhos. */
@OptIn(ExperimentalTestApi::class)
class AssinaturaCardTest {
    private val tamanhos = listOf(Size(390f, 1600f), Size(834f, 1600f), Size(1280f, 1600f))
    private val basico = PlanoResposta("BASICO", "Básico", 29.9, 20, 299.0)
    private val consultorio = PlanoResposta("CONSULTORIO", "Consultório", 79.9, 80, 799.0)
    private val plus = PlanoResposta("PLUS", "Plus", 179.9, null, 1799.0)
    private fun assinatura(status: StatusAssinatura = StatusAssinatura.TESTE, plano: PlanoResposta = PlanoResposta("TESTE", "Teste", 0.0, 5), ocupados: Int = 4,
        exibe: Boolean? = true, troca: TrocaPlanoAgendadaDto? = null) =
        AssinaturaResposta(Uuid.random(), plano, status, ocupados, null, null, true, listOf(basico, consultorio, plus), Periodicidade.MENSAL, 9, exibe, troca)

    @Test fun testeComparaPlanosNosTresTamanhos() = tamanhos.forEach { tamanho ->
        runSkikoComposeUiTest(size = tamanho) {
            var periodicidade: Periodicidade? = null; var escolhido: String? = null; var confirmou = 0
            val estado = EstadoAssinatura(carregado = true, assinatura = assinatura(), periodicidade = Periodicidade.ANUAL, planoEscolhido = "CONSULTORIO")
            setContent { DietaTheme { AssinaturaCard(estado, AcoesAssinatura({ confirmou++ }, {}, {}, { periodicidade = it }, { escolhido = it })) } }
            onNodeWithText("Teste grátis").assertExists()
            onNodeWithText("Faltam 9 dias").assertExists()
            onNodeWithText("Pacientes 4 de 5").assertExists()
            onNodeWithText("2 meses grátis").assertExists()
            onNodeWithText("R$ 799,00 por ano · R$ 66,58/mês").assertExists()
            onNodeWithText("Pacientes sem limite").assertExists()
            onNodeWithTag("plano-CONSULTORIO").assertIsSelected()
            onNodeWithTag("periodicidade-MENSAL").performClick(); assertEquals(Periodicidade.MENSAL, periodicidade)
            onNodeWithTag("plano-PLUS").performClick(); assertEquals("PLUS", escolhido)
            onNodeWithText("Assinar Consultório anual").performClick(); assertEquals(1, confirmou)
            onNodeWithText("Abre o navegador. PIX, cartão ou boleto.").assertExists()
        }
    }

    @Test fun ativaPertoDoLimiteMostraSeuPlanoEMudar() = runSkikoComposeUiTest(size = Size(390f, 1600f)) {
        val a = assinatura(StatusAssinatura.ATIVA, basico, ocupados = 18)
        setContent { DietaTheme { AssinaturaCard(EstadoAssinatura(carregado = true, assinatura = a, planoEscolhido = "BASICO"), AcoesAssinatura({}, {}, {})) } }
        onNodeWithText("Básico · mensal").assertExists()
        onNodeWithText("Perto do limite: com 20, novos convites ficam bloqueados.").assertExists()
        onNodeWithText("Seu plano").assertExists()
        onNodeWithTag("assinatura-confirmar").assertDoesNotExist()
        onNodeWithText("R$ 29,90 por mês").assertExists()
        onNodeWithText("Subir vale na hora", substring = true).assertExists()
    }

    @Test fun ativaComOutroPlanoEscolhidoOfereceMudar() = runSkikoComposeUiTest(size = Size(390f, 1600f)) {
        val a = assinatura(StatusAssinatura.ATIVA, basico, troca = TrocaPlanoAgendadaDto("BASICO", Periodicidade.MENSAL, LocalDate(2026, 12, 5)))
        setContent { DietaTheme { AssinaturaCard(EstadoAssinatura(carregado = true, assinatura = a, planoEscolhido = "CONSULTORIO", trocaAgendadaPara = LocalDate(2026, 12, 5)), AcoesAssinatura({}, {}, {})) } }
        onNodeWithText("Mudar para Consultório").assertIsEnabled()
        onNodeWithText("Pronto: a troca vale a partir de 05/12/2026.").assertExists()
        onNodeWithText("Troca para Básico agendada para 05/12/2026. Até lá vale o plano atual.").assertExists()
    }

    @Test fun semChaveMostraSoPlanosELimites() = runSkikoComposeUiTest(size = Size(390f, 1600f)) {
        setContent { DietaTheme { AssinaturaCard(EstadoAssinatura(carregado = true, assinatura = assinatura(exibe = false), planoEscolhido = "BASICO"), AcoesAssinatura({}, {}, {})) } }
        onNodeWithText("Básico: até 20 pacientes").assertExists()
        onNodeWithText("Plus: pacientes sem limite").assertExists()
        onNodeWithText("A assinatura é gerenciada fora deste aplicativo.").assertExists()
        onNodeWithText("R$", substring = true).assertDoesNotExist()
        onNodeWithTag("assinatura-confirmar").assertDoesNotExist()
    }

    @Test fun semConexaoDesabilitaOBotao() = runSkikoComposeUiTest(size = Size(390f, 1600f)) {
        setContent { DietaTheme { AssinaturaCard(EstadoAssinatura(carregado = true, assinatura = assinatura(), planoEscolhido = "BASICO", semConexao = true, erro = MensagemAssinatura.SEM_CONEXAO), AcoesAssinatura({}, {}, {})) } }
        onNodeWithText("Assinar Básico mensal").assertIsNotEnabled()
    }
}
