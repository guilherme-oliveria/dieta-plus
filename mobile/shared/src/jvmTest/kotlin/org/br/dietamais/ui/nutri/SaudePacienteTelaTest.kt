package org.br.dietamais.ui.nutri

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.saude.ErroItemSaude
import org.br.dietamais.dominio.saude.FormularioItemSaude
import org.br.dietamais.ui.nutri.paciente.*
import org.br.dietamais.ui.nutri.pacientes.AbaFicha
import org.br.dietamais.ui.nutri.pacientes.EstadoPacientes
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-82, artboards `A-Saude-Ficha` e `A-Saude-Item` (DES-16), nos três tamanhos. */
@OptIn(ExperimentalTestApi::class)
class SaudePacienteTelaTest {
    private val tamanhos = listOf(Size(390f, 844f), Size(834f, 1112f), Size(1280f, 900f))
    private val alergia = RestricaoSaudeDto(Uuid.random(), TipoRestricaoSaude.ALERGIA, "Amendoim", GravidadeRestricao.GRAVE, "Anafilaxia em 2024.", listOf("AMENDOIM"))
    private val intolerancia = RestricaoSaudeDto(Uuid.random(), TipoRestricaoSaude.INTOLERANCIA, "Lactose", GravidadeRestricao.MODERADA, marcadores = listOf("CONTEM_LACTOSE"))
    private val medicamento = RestricaoSaudeDto(Uuid.random(), TipoRestricaoSaude.MEDICAMENTO, "Losartana 50 mg")
    private val instante = Instant.parse("2026-11-18T12:00:00Z")
    private fun paciente(restricoes: List<RestricaoSaudeDto>?, status: StatusVinculo = StatusVinculo.ATIVO) =
        PacienteResposta(Uuid.random(), Uuid.random(), "Ana Lima", status = status, versao = 1, criadoEm = instante, atualizadoEm = instante, restricoesSaude = restricoes)

    @Test fun listaMostraOsItensNaOrdemEOAvisoDePrivacidade() = tamanhos.forEach { tamanho ->
        runSkikoComposeUiTest(size = tamanho) {
            var editado: Uuid? = null
            setContent { DietaTheme { SaudePacienteTela(EstadoSaudePaciente(paciente(listOf(medicamento, intolerancia, alergia))), AcoesSaudePaciente(aoEditar = { editado = it })) } }
            onNodeWithText("Saúde e restrições").assertExists()
            onNodeWithText("Amendoim").assertExists(); onNodeWithText("Grave").assertExists(); onNodeWithText("Marca: Amendoim").assertExists()
            onNodeWithText("Marca: Leite e lactose").assertExists(); onNodeWithText("Losartana 50 mg").assertExists()
            onNodeWithText("Só você vê esta seção", substring = true).assertExists()
            onNodeWithTag("saude-editar-${alergia.id}").performClick(); assertEquals(alergia.id, editado)
        }
    }

    @Test fun formularioMostraMarcadoresSoEmAlergiaEIntolerancia() = tamanhos.forEach { tamanho ->
        runSkikoComposeUiTest(size = tamanho) {
            var tipo: TipoRestricaoSaude? = null; var marcador: String? = null
            var estado by androidx.compose.runtime.mutableStateOf(EstadoSaudePaciente(paciente(emptyList()), formulario = FormularioItemSaude(nome = "Amendoim", marcadores = setOf("AMENDOIM"))))
            setContent { DietaTheme { SaudePacienteTela(estado, AcoesSaudePaciente(aoTipo = { tipo = it }, aoMarcador = { marcador = it })) } }
            onNodeWithText("Novo item de saúde").assertExists()
            onNodeWithTag("saude-marcador-AMENDOIM").assertIsSelected()
            onNodeWithTag("saude-marcador-SOJA").assertIsNotSelected().performClick(); assertEquals("SOJA", marcador)
            onNodeWithText("Sugerido pelo nome. Só alergia e intolerância avisam no montador.").assertExists()
            onNodeWithTag("saude-tipo-MEDICAMENTO").performClick(); assertEquals(TipoRestricaoSaude.MEDICAMENTO, tipo)
            estado = estado.copy(formulario = FormularioItemSaude(tipo = TipoRestricaoSaude.MEDICAMENTO, nome = "Losartana"))
            waitForIdle()
            onNodeWithTag("saude-marcador-AMENDOIM").assertDoesNotExist()
            onNodeWithTag("saude-adicionar").assertDoesNotExist()
        }
    }

    @Test fun erroDoNomeEVinculoEncerradoSemEdicao() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        var estado by androidx.compose.runtime.mutableStateOf(EstadoSaudePaciente(paciente(emptyList()), formulario = FormularioItemSaude(), erroFormulario = ErroItemSaude.NOME_OBRIGATORIO))
        setContent { DietaTheme { SaudePacienteTela(estado, AcoesSaudePaciente()) } }
        onNodeWithText("Informe o nome.").assertExists()
        estado = EstadoSaudePaciente(paciente(listOf(alergia), StatusVinculo.ENCERRADO))
        waitForIdle()
        onNodeWithTag("saude-adicionar").assertDoesNotExist(); onNodeWithTag("saude-editar-${alergia.id}").assertDoesNotExist()
    }

    @Test fun removerAbreConfirmacao() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        var confirmou = false
        setContent { DietaTheme { SaudePacienteTela(EstadoSaudePaciente(paciente(listOf(alergia)), removendo = alergia.id), AcoesSaudePaciente(aoConfirmarRemocao = { confirmou = true })) } }
        onNodeWithText("Remover item de saúde?").assertExists()
        onNodeWithText("Amendoim sai da ficha. As dietas já publicadas não mudam.").assertExists()
        onAllNodesWithText("Remover").filterToOne(hasAnyAncestor(hasTestTag("saude-remover-dialogo"))).performClick(); assertTrue(confirmou)
    }

    @Test fun fichaMostraOSeloEAAbaSaude() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        var aba: AbaFicha? = null
        setContent { DietaTheme { PacienteFichaTela(EstadoPacientes(selecionado = paciente(listOf(intolerancia, alergia, medicamento))), { aba = it }, {}, {}) } }
        onNodeWithTag("selo-saude").assertExists()
        onNodeWithText("Alergia grave: amendoim · +1").assertExists()
        onNodeWithText("Saúde").performClick(); assertEquals(AbaFicha.SAUDE, aba)
    }

    @Test fun semAlergiaNemIntoleranciaNaoTemSelo() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        setContent { DietaTheme { PacienteFichaTela(EstadoPacientes(selecionado = paciente(listOf(medicamento))), {}, {}, {}) } }
        onNodeWithTag("selo-saude").assertDoesNotExist()
    }
}
