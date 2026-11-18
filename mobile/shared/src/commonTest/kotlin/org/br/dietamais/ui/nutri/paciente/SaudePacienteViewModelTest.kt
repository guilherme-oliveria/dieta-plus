package org.br.dietamais.ui.nutri.paciente

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.br.dietamais.data.remoto.ErroApi
import org.br.dietamais.data.remoto.FalhaComunicacao
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.PortaPacientes
import org.br.dietamais.dominio.saude.ErroItemSaude
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-82 (RN-SAU-01 e 02): seção "Saúde e restrições" da ficha. */
@OptIn(ExperimentalCoroutinesApi::class)
class SaudePacienteViewModelTest {
    private class Porta : PortaPacientes {
        var falha: Exception? = null; val gravados = mutableListOf<List<RestricaoSaudeDto>>()
        override fun observar(): Flow<List<PacienteResposta>> = flowOf(emptyList())
        override suspend fun atualizar() {}
        override suspend fun criar(requisicao: CriarPacienteRequisicao, chave: Uuid): PacienteResposta = error("não usado")
        override suspend fun convidar(id: Uuid, chave: Uuid): ConviteCriadoResposta = error("não usado")
        override suspend fun assinatura(): AssinaturaResumoResposta? = null
        override suspend fun salvarRestricoes(paciente: PacienteResposta, restricoes: List<RestricaoSaudeDto>): PacienteResposta {
            falha?.let { throw it }; gravados += restricoes; return paciente.copy(restricoesSaude = restricoes, versao = paciente.versao + 1)
        }
    }
    private val instante = Instant.parse("2026-11-18T12:00:00Z")
    private fun paciente(restricoes: List<RestricaoSaudeDto>? = null, status: StatusVinculo = StatusVinculo.ATIVO) =
        PacienteResposta(Uuid.random(), Uuid.random(), "Paciente fictício", status = status, versao = 3, criadoEm = instante, atualizadoEm = instante, restricoesSaude = restricoes)
    private val idFixo = Uuid.parse("019a9000-0000-7000-8000-0000000000aa")
    private fun TestScope.vm(p: Porta) = SaudePacienteViewModel(p, StandardTestDispatcher(testScheduler)) { idFixo }

    @Test fun novaAlergiaSugereMarcadorPeloNomeESalvaALista() = runTest {
        val porta = Porta(); val vm = vm(porta); vm.abrir(paciente())
        vm.novoItem(); vm.mudarNome("Amendoim"); vm.mudarGravidade(GravidadeRestricao.GRAVE); vm.mudarObservacao("Carrega adrenalina")
        assertEquals(setOf("AMENDOIM"), vm.estado.value.formulario!!.marcadores)
        vm.salvar(); advanceUntilIdle()
        val salvo = porta.gravados.single().single()
        assertEquals(RestricaoSaudeDto(idFixo, TipoRestricaoSaude.ALERGIA, "Amendoim", GravidadeRestricao.GRAVE, "Carrega adrenalina", listOf("AMENDOIM")), salvo)
        assertNull(vm.estado.value.formulario); assertEquals(MensagemSaude.SALVO, vm.estado.value.aviso); assertEquals(4, vm.estado.value.paciente!!.versao)
    }

    @Test fun marcadorEscolhidoPelaNutriNaoMudaComONomeECondicaoNaoTemMarcador() = runTest {
        val vm = vm(Porta()); vm.abrir(paciente()); vm.novoItem()
        vm.mudarNome("Leite"); vm.alternarMarcador("CONTEM_LACTOSE"); vm.alternarMarcador("SOJA")
        vm.mudarNome("Leite de vaca e amendoim")
        assertEquals(setOf("SOJA"), vm.estado.value.formulario!!.marcadores)
        vm.mudarTipo(TipoRestricaoSaude.CONDICAO); assertTrue(vm.estado.value.formulario!!.marcadores.isEmpty())
        vm.alternarMarcador("OVO"); assertTrue(vm.estado.value.formulario!!.marcadores.isEmpty())
        vm.mudarGravidade(GravidadeRestricao.LEVE); vm.mudarGravidade(GravidadeRestricao.LEVE); assertNull(vm.estado.value.formulario!!.gravidade) // toque de novo desmarca
    }

    @Test fun nomeVazioNaoSalvaEEditarSubstituiOItem() = runTest {
        val porta = Porta(); val existente = RestricaoSaudeDto(Uuid.random(), TipoRestricaoSaude.INTOLERANCIA, "Lactose", marcadores = listOf("CONTEM_LACTOSE"))
        val vm = vm(porta); vm.abrir(paciente(listOf(existente)))
        vm.novoItem(); vm.salvar(); advanceUntilIdle()
        assertEquals(ErroItemSaude.NOME_OBRIGATORIO, vm.estado.value.erroFormulario); assertTrue(porta.gravados.isEmpty())
        vm.cancelar(); vm.editar(existente.id); vm.mudarGravidade(GravidadeRestricao.MODERADA); vm.salvar(); advanceUntilIdle()
        assertEquals(listOf(existente.copy(gravidade = GravidadeRestricao.MODERADA)), porta.gravados.single())
    }

    @Test fun removerPedeConfirmacao() = runTest {
        val porta = Porta(); val a = RestricaoSaudeDto(Uuid.random(), TipoRestricaoSaude.CONDICAO, "Hipertensão"); val b = RestricaoSaudeDto(Uuid.random(), TipoRestricaoSaude.MEDICAMENTO, "Losartana")
        val vm = vm(porta); vm.abrir(paciente(listOf(a, b)))
        vm.pedirRemocao(a.id); assertEquals(a.id, vm.estado.value.removendo); vm.cancelarRemocao(); assertNull(vm.estado.value.removendo)
        vm.pedirRemocao(a.id); vm.confirmarRemocao(); advanceUntilIdle()
        assertEquals(listOf(b), porta.gravados.single()); assertEquals(MensagemSaude.REMOVIDO, vm.estado.value.aviso)
    }

    @Test fun vinculoEncerradoSoLeEFalhasViramMensagem() = runTest {
        val porta = Porta(); val vm = vm(porta); vm.abrir(paciente(status = StatusVinculo.ENCERRADO))
        vm.novoItem(); assertNull(vm.estado.value.formulario)
        vm.abrir(paciente()); vm.novoItem(); vm.mudarNome("Ovo")
        porta.falha = ErroApi("ITEM_INVALIDO", "RN-SAU-02", emptyList(), 422, null, null); vm.salvar(); advanceUntilIdle()
        assertEquals(MensagemSaude.ITEM_INVALIDO, vm.estado.value.erro); assertNotNull(vm.estado.value.formulario)
        porta.falha = FalhaComunicacao(); vm.salvar(); advanceUntilIdle(); assertEquals(MensagemSaude.SEM_CONEXAO, vm.estado.value.erro)
        porta.falha = ErroApi("ASSINATURA_INATIVA", "RN-ASS-04", emptyList(), 422, null, null); vm.salvar(); advanceUntilIdle()
        assertEquals(MensagemSaude.ASSINATURA_INATIVA, vm.estado.value.erro); assertFalse(vm.estado.value.enviando)
    }

    @Test fun abrirOutroPacienteFechaOFormularioEOMesmoSoAtualiza() = runTest {
        val vm = vm(Porta()); val p = paciente(); vm.abrir(p); vm.novoItem(); vm.mudarNome("Soja")
        vm.abrir(p.copy(versao = 9)); assertNotNull(vm.estado.value.formulario); assertEquals(9, vm.estado.value.paciente!!.versao)
        vm.abrir(paciente()); assertNull(vm.estado.value.formulario)
    }
}
