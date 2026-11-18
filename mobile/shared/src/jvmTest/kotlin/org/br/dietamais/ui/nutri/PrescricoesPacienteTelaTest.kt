package org.br.dietamais.ui.nutri

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.dto.ModeloOrientacaoResposta
import org.br.dietamais.data.remoto.dto.SuplementoResposta
import org.br.dietamais.dominio.prescricoes.*
import org.br.dietamais.ui.nutri.prescricoes.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class PrescricoesPacienteTelaTest {
    private val t = Instant.parse("2026-11-01T12:00:00Z")
    private val paciente = Uuid.random()
    private val hoje = LocalDate(2026, 11, 18)
    private val vitamina = SuplementoResposta(Uuid.random(), paciente, produtoId = Uuid.random(), nome = "Vitamina D3", dose = "1 cápsula", forma = "Cápsula",
        frequencia = "Todo dia", horarios = listOf("08:00"), inicio = LocalDate(2026, 11, 1), versao = 1, criadoEm = t, atualizadoEm = t)
    private val ferro = SuplementoResposta(Uuid.random(), paciente, nome = "Ferro", dose = "20 mg", inicio = LocalDate(2026, 9, 1), fim = LocalDate(2026, 10, 1),
        versao = 1, criadoEm = t, atualizadoEm = t)
    private val tamanhos = listOf(Size(360f, 800f), Size(768f, 1024f), Size(1280f, 900f))

    @Test fun listaEmUsoEEncerradosNosTresTamanhos() {
        tamanhos.forEach { tamanho ->
            runSkikoComposeUiTest(size = tamanho) {
                var encerrados = 0
                var editado: Uuid? = null
                setContent { DietaTheme { PrescricoesPacienteTela(EstadoPrescricoes(paciente, hoje = hoje, carregado = true, suplementos = listOf(vitamina, ferro)),
                    AcoesPrescricoes(aoEncerrados = { encerrados++ }, aoEditarSuplemento = { editado = it })) } }
                onNodeWithText("Vitamina D3").assertExists()
                onNodeWithText("Meu produto").assertExists()
                onNodeWithText("1 cápsula · Cápsula").assertExists()
                onNodeWithText("Todo dia · 08:00").assertExists()
                onNodeWithText("Desde 01/11/2026, sem fim").assertExists()
                onNodeWithText("Ferro").assertDoesNotExist()
                onNodeWithText("Encerrados (1)").performClick()
                assertEquals(1, encerrados)
                onNodeWithContentDescription("Editar Vitamina D3").performClick()
                assertEquals(vitamina.id, editado)
            }
        }
    }

    @Test fun encerradosAbertosMostramPeriodoSemEditar() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        setContent { DietaTheme { PrescricoesPacienteTela(EstadoPrescricoes(paciente, hoje = hoje, carregado = true, suplementos = listOf(ferro), mostrarEncerrados = true),
            AcoesPrescricoes()) } }
        onNodeWithText("Nenhum suplemento em uso.").assertExists()
        onNodeWithText("01/09/2026 a 01/10/2026").assertExists()
        onNodeWithContentDescription("Editar Ferro").assertDoesNotExist()
    }

    @Test fun semConexaoDesabilitaPrescrever() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        setContent { DietaTheme { PrescricoesPacienteTela(EstadoPrescricoes(paciente, hoje = hoje, carregado = true, semConexao = true), AcoesPrescricoes()) } }
        onNodeWithText("Prescrever").assertIsNotEnabled()
    }

    @Test fun formularioDeSuplementoMostraErrosEEscolheForma() = runSkikoComposeUiTest(size = Size(390f, 1600f)) {
        var forma: String? = null
        var origem: OrigemSuplemento? = null
        setContent { DietaTheme { FormularioSuplementoConteudo(EstadoPrescricoes(paciente, hoje = hoje, carregado = true,
            suplemento = novoFormularioSuplemento(hoje, "Todo dia").copy(origem = OrigemSuplemento.TEXTO_LIVRE),
            errosSuplemento = setOf(ErroSuplemento.NOME, ErroSuplemento.DOSE)),
            AcoesPrescricoes(aoForma = { forma = it }, aoOrigem = { origem = it })) } }
        onNodeWithText("Prescrever suplemento").assertExists()
        onNodeWithText("Informe o nome (até 120 caracteres).").assertExists()
        onNodeWithText("Informe a dose (até 80 caracteres).").assertExists()
        onNodeWithText(FORMAS_SUPLEMENTO.first()).performClick()
        assertEquals(FORMAS_SUPLEMENTO.first(), forma)
        onNodeWithText("Meu produto").performClick()
        assertEquals(OrigemSuplemento.PRODUTO, origem)
    }

    @Test fun edicaoCongelaNomeEOfereceEncerrar() = runSkikoComposeUiTest(size = Size(390f, 1600f)) {
        var encerrar = 0
        setContent { DietaTheme { FormularioSuplementoConteudo(EstadoPrescricoes(paciente, hoje = hoje, carregado = true, suplemento = formularioDe(vitamina)),
            AcoesPrescricoes(aoEncerrarSuplemento = { encerrar++ })) } }
        onNodeWithText("Editar suplemento").assertExists()
        onNodeWithText("Nome e produto não mudam depois de prescritos.").assertExists()
        onNodeWithText("Encerrar hoje").performClick()
        assertEquals(1, encerrar)
    }

    @Test fun formularioDeOrientacaoTemNegritoEEnvia() = runSkikoComposeUiTest(size = Size(390f, 1400f)) {
        var negrito = 0
        var enviar = 0
        var salvarModelo = 0
        setContent { DietaTheme { androidx.compose.foundation.layout.Box(Modifier.verticalScroll(rememberScrollState())) {
            FormularioOrientacaoConteudo(EstadoPrescricoes(paciente, hoje = hoje, carregado = true, orientacao = FormularioOrientacao("Hidratação", "Beba água")),
                "Ana", AcoesPrescricoes(aoNegrito = { negrito++ }, aoEnviar = { enviar++ }, aoSalvarModelo = { salvarModelo++ }))
        } } }
        onNodeWithText("Para Ana").assertExists()
        onNodeWithContentDescription("Negrito").performClick()
        assertEquals(1, negrito)
        onNodeWithText("Salvar também como modelo").performClick()
        assertEquals(1, salvarModelo)
        onNodeWithText("Enviar").performClick()
        assertEquals(1, enviar)
    }

    @Test fun listaDeModelosUsaEExclui() = runSkikoComposeUiTest(size = Size(390f, 1000f)) {
        val modelo = ModeloOrientacaoResposta(Uuid.random(), "Pré-treino", "Coma 1h antes", 1, t, t)
        var usado: Uuid? = null
        var excluido: Uuid? = null
        setContent { DietaTheme { FormularioOrientacaoConteudo(EstadoPrescricoes(paciente, hoje = hoje, carregado = true, orientacao = FormularioOrientacao(),
            modelos = listOf(modelo), escolhendoModelo = true), "Ana", AcoesPrescricoes(aoEscolherModelo = { usado = it }, aoExcluirModelo = { excluido = it })) } }
        onNodeWithText("Meus modelos de orientação").assertExists()
        onNodeWithContentDescription("Usar o modelo Pré-treino").performClick()
        assertEquals(modelo.id, usado)
        onNodeWithContentDescription("Excluir o modelo Pré-treino").performClick()
        assertEquals(modelo.id, excluido)
    }

    @Test fun abaOrientacoesListaEArquiva() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        val o = org.br.dietamais.data.remoto.dto.OrientacaoResposta(Uuid.random(), paciente, "Hidratação", "Beba 2 L de água", versao = 1, criadoEm = t, atualizadoEm = t)
        var arquivada: Uuid? = null
        setContent { DietaTheme { PrescricoesPacienteTela(EstadoPrescricoes(paciente, secao = SecaoPrescricoes.ORIENTACOES, hoje = hoje, carregado = true, orientacoes = listOf(o)),
            AcoesPrescricoes(aoArquivar = { arquivada = it })) } }
        onNodeWithText("Hidratação").assertExists()
        onNodeWithContentDescription("Arquivar Hidratação").performClick()
        assertEquals(o.id, arquivada)
    }
}
