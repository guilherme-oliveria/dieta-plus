package org.br.dietamais.ui.nutri.paciente

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.br.dietamais.data.remoto.FalhaComunicacao
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.PortaAdesao
import kotlin.test.*
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class AdesaoPacienteViewModelTest {
    private class Porta : PortaAdesao {
        val pedidos = mutableListOf<Uuid>(); var falha: Exception? = null
        override suspend fun obter(pacienteId: Uuid): AdesaoDto {
            pedidos += pacienteId; falha?.let { throw it }
            return AdesaoDto(pacienteId, JanelaAdesaoDto(7, 0, 0, 0, 0), JanelaAdesaoDto(30, 0, 0, 0, 0), HumorAdesaoDto(3.8, 2.7, 12))
        }
    }

    @Test fun abrirBuscaUmaVezPorPacienteETrocaLimpaOAnterior() = runTest {
        val porta = Porta(); val vm = AdesaoPacienteViewModel(porta, StandardTestDispatcher(testScheduler))
        val ana = Uuid.random(); val bia = Uuid.random()
        vm.abrir(ana); advanceUntilIdle(); vm.abrir(ana); advanceUntilIdle()
        assertEquals(listOf(ana), porta.pedidos); assertEquals(ana, vm.estado.value.adesao?.pacienteId)
        vm.abrir(bia); assertNull(vm.estado.value.adesao); assertTrue(vm.estado.value.carregando)
        advanceUntilIdle(); assertEquals(bia, vm.estado.value.adesao?.pacienteId)
    }

    @Test fun semRedeAvisaEBuscaQuandoAConexaoVolta() = runTest {
        val porta = Porta(); val online = MutableStateFlow(false)
        val vm = AdesaoPacienteViewModel(porta, StandardTestDispatcher(testScheduler), online); advanceUntilIdle()
        val ana = Uuid.random()
        vm.abrir(ana); advanceUntilIdle()
        assertEquals(MensagemAdesao.SEM_CONEXAO, vm.estado.value.erro); assertTrue(porta.pedidos.isEmpty())
        online.value = true; advanceUntilIdle()
        assertEquals(listOf(ana), porta.pedidos); assertNull(vm.estado.value.erro); assertNotNull(vm.estado.value.adesao)
    }

    @Test fun falhaMostraErroERecarregarTentaDeNovo() = runTest {
        val porta = Porta().apply { falha = IllegalStateException("x") }
        val vm = AdesaoPacienteViewModel(porta, StandardTestDispatcher(testScheduler)); val ana = Uuid.random()
        vm.abrir(ana); advanceUntilIdle()
        assertEquals(MensagemAdesao.ERRO, vm.estado.value.erro); assertFalse(vm.estado.value.carregando)
        porta.falha = FalhaComunicacao(); vm.recarregar(); advanceUntilIdle()
        assertEquals(MensagemAdesao.SEM_CONEXAO, vm.estado.value.erro)
        porta.falha = null; vm.recarregar(); advanceUntilIdle()
        assertNull(vm.estado.value.erro); assertEquals(3, porta.pedidos.size)
    }
}
