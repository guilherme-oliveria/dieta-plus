package org.br.dietamais.ui.nutri.assinatura

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.br.dietamais.data.remoto.ErroApi
import org.br.dietamais.data.remoto.FalhaComunicacao
import org.br.dietamais.data.remoto.dto.AntecedenciaLembrete
import org.br.dietamais.data.remoto.dto.PreferenciasAgendaDto
import org.br.dietamais.data.repositorio.PortaPreferenciasAgenda
import kotlin.test.*

/** MOB-86 (RN-AGE-05): preferência de lembrete da nutri, online. */
@OptIn(ExperimentalCoroutinesApi::class)
class LembreteConsultasViewModelTest {
    private class Porta(var atual: PreferenciasAgendaDto = PreferenciasAgendaDto(AntecedenciaLembrete.UM_DIA, false)) : PortaPreferenciasAgenda {
        var falha: Exception? = null; val enviados = mutableListOf<PreferenciasAgendaDto>()
        override suspend fun obter(): PreferenciasAgendaDto { falha?.let { throw it }; return atual }
        override suspend fun salvar(preferencias: PreferenciasAgendaDto): PreferenciasAgendaDto { falha?.let { throw it }; enviados += preferencias; atual = preferencias; return preferencias }
    }
    private fun TestScope.vm(p: Porta, online: kotlinx.coroutines.flow.Flow<Boolean> = flowOf(true)) = LembreteConsultasViewModel(p, StandardTestDispatcher(testScheduler), online)

    @Test fun carregaOSalvoEValorDesconhecidoCaiNoPadrao() = runTest {
        val vm = vm(Porta(PreferenciasAgendaDto(AntecedenciaLembrete.DOIS_DIAS, true))); advanceUntilIdle()
        assertTrue(vm.estado.value.carregado); assertEquals(AntecedenciaLembrete.DOIS_DIAS, vm.estado.value.antecedencia); assertTrue(vm.estado.value.extraUmaHora)
        val outro = vm(Porta(PreferenciasAgendaDto(AntecedenciaLembrete.OUTRO, false))); advanceUntilIdle()
        assertEquals(AntecedenciaLembrete.UM_DIA, outro.estado.value.antecedencia)
    }

    @Test fun salvaSoQuandoMudaEAvisa() = runTest {
        val p = Porta(); val vm = vm(p); advanceUntilIdle()
        vm.salvar(); advanceUntilIdle(); assertTrue(p.enviados.isEmpty())
        vm.mudarAntecedencia(AntecedenciaLembrete.OUTRO); assertFalse(vm.estado.value.alterado) // valor desconhecido não é escolha
        vm.mudarAntecedencia(AntecedenciaLembrete.DUAS_HORAS); vm.mudarExtraUmaHora(true); vm.salvar(); advanceUntilIdle()
        assertEquals(listOf(PreferenciasAgendaDto(AntecedenciaLembrete.DUAS_HORAS, true)), p.enviados)
        assertEquals(MensagemLembrete.SALVO, vm.estado.value.aviso); assertFalse(vm.estado.value.alterado); assertFalse(vm.estado.value.enviando)
        vm.mudarExtraUmaHora(false); assertNull(vm.estado.value.aviso)
    }

    @Test fun semRedeNaoSalvaEFalhasViramMensagem() = runTest {
        val p = Porta(); val rede = MutableStateFlow(true); val vm = vm(p, rede); advanceUntilIdle()
        rede.value = false; runCurrent()
        vm.mudarAntecedencia(AntecedenciaLembrete.DOIS_DIAS); vm.salvar(); advanceUntilIdle()
        assertTrue(p.enviados.isEmpty()); assertEquals(MensagemLembrete.SEM_CONEXAO, vm.estado.value.erro)
        rede.value = true; runCurrent()
        p.falha = ErroApi("ASSINATURA_INATIVA", "RN-ASS-04", emptyList(), 422, null, null); vm.salvar(); advanceUntilIdle()
        assertEquals(MensagemLembrete.ASSINATURA_INATIVA, vm.estado.value.erro); assertTrue(vm.estado.value.alterado)
        p.falha = FalhaComunicacao(); vm.salvar(); advanceUntilIdle()
        assertEquals(MensagemLembrete.SEM_CONEXAO, vm.estado.value.erro)
    }

    @Test fun leituraQueFalhaNaoDeixaSalvar() = runTest {
        val p = Porta().apply { falha = FalhaComunicacao() }; val vm = vm(p); advanceUntilIdle()
        assertFalse(vm.estado.value.carregado); vm.mudarExtraUmaHora(true); p.falha = null; vm.salvar(); advanceUntilIdle()
        assertTrue(p.enviados.isEmpty())
        vm.recarregar(); advanceUntilIdle(); assertTrue(vm.estado.value.carregado)
    }
}
