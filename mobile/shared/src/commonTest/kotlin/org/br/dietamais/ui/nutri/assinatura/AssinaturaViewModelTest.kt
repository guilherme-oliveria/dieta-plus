package org.br.dietamais.ui.nutri.assinatura

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.ErroApi
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.PortaAssinatura
import org.br.dietamais.dominio.assinatura.AcaoPlano
import kotlin.test.*
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class AssinaturaViewModelTest {
    private val basico = PlanoResposta("BASICO_20", "Básico", 29.9, 20)
    private fun assinatura(status: StatusAssinatura = StatusAssinatura.TESTE, pagamento: Boolean = true) =
        AssinaturaResposta(Uuid.random(), PlanoResposta("TESTE", "Teste", 0.0, 5), status, 2, null, null, pagamento, listOf(basico))
    private class Porta(var atual: AssinaturaResposta) : PortaAssinatura {
        var falha: Exception? = null; var url: String? = "https://pagamento.exemplo/abc"; var agendada: LocalDate? = null
        var pedidos = mutableListOf<String>(); var periodicidades = mutableListOf<Periodicidade>()
        override suspend fun obter() = atual
        override suspend fun pedirCheckout(plano: String, periodicidade: Periodicidade): CheckoutResposta {
            falha?.let { throw it }; pedidos += plano; periodicidades += periodicidade; return CheckoutResposta(url, agendada)
        }
    }
    private fun TestScope.vm(p: Porta, online: Boolean = true) = AssinaturaViewModel(p, StandardTestDispatcher(testScheduler), flowOf(online))

    @Test fun carregaEPedeCheckoutDeUmPlanoOferecido() = runTest {
        val p = Porta(assinatura()); val vm = vm(p); advanceUntilIdle()
        assertTrue(vm.estado.value.carregado)
        vm.assinar("BASICO_20"); advanceUntilIdle()
        assertEquals("https://pagamento.exemplo/abc", vm.estado.value.urlCheckout); vm.urlAberta(); assertNull(vm.estado.value.urlCheckout)
    }

    @Test fun ignoraPlanoForaDaListaOuPagamentoIndisponivel() = runTest {
        val p = Porta(assinatura()); val vm = vm(p); advanceUntilIdle()
        vm.assinar("OUTRO"); advanceUntilIdle(); assertTrue(p.pedidos.isEmpty())
        p.atual = assinatura(pagamento = false); vm.recarregar(); advanceUntilIdle()
        vm.assinar("BASICO_20"); advanceUntilIdle(); assertTrue(p.pedidos.isEmpty())
    }

    @Test fun recusaUrlSemHttps() = runTest {
        val p = Porta(assinatura()); p.url = "http://inseguro.exemplo"; val vm = vm(p); advanceUntilIdle()
        vm.assinar("BASICO_20"); advanceUntilIdle()
        assertNull(vm.estado.value.urlCheckout); assertEquals(MensagemAssinatura.ERRO, vm.estado.value.erro)
    }

    @Test fun jaAtivaRecarregaEMostraAviso() = runTest {
        val p = Porta(assinatura()); val vm = vm(p); advanceUntilIdle()
        p.falha = ErroApi("ASSINATURA_JA_ATIVA", null, emptyList(), 409, null, null); p.atual = assinatura(StatusAssinatura.ATIVA)
        vm.assinar("BASICO_20"); advanceUntilIdle()
        assertEquals(MensagemAssinatura.JA_ATIVA, vm.estado.value.erro); assertEquals(StatusAssinatura.ATIVA, vm.estado.value.assinatura?.status)
    }

    @Test fun somenteLeituraEAtrasoSaoDerivadosDoStatus() = runTest {
        val p = Porta(assinatura(StatusAssinatura.CANCELADA)); val vm = vm(p); advanceUntilIdle()
        assertTrue(vm.estado.value.somenteLeitura)
        p.atual = assinatura(StatusAssinatura.EM_ATRASO); vm.recarregar(); advanceUntilIdle()
        assertTrue(vm.estado.value.emAtraso); assertFalse(vm.estado.value.somenteLeitura)
    }

    @Test fun semConexaoNaoChamaAApi() = runTest {
        val vm = vm(Porta(assinatura()), online = false); advanceUntilIdle()
        assertFalse(vm.estado.value.carregado); assertEquals(MensagemAssinatura.SEM_CONEXAO, vm.estado.value.erro)
    }

    @Test fun formataPreco() {
        assertEquals("R$ 29,90", preco(29.9)); assertEquals("R$ 100,05", preco(100.05)); assertEquals("R$ 1.799,00", preco(1799.0)); assertEquals("R$ 0,00", preco(0.0))
    }

    private val consultorio = PlanoResposta("CONSULTORIO", "Consultório", 79.9, 80, 799.0)
    private val anual = PlanoResposta("BASICO_20", "Básico", 29.9, 20, 299.0)
    private fun comparacao(status: StatusAssinatura = StatusAssinatura.TESTE, plano: PlanoResposta = PlanoResposta("TESTE", "Teste", 0.0, 5),
        periodicidade: Periodicidade? = null) =
        AssinaturaResposta(Uuid.random(), plano, status, 4, null, null, true, listOf(anual, consultorio), periodicidade, 9, true)

    @Test fun alternaPeriodicidadeEAssinaOPlanoEscolhido() = runTest {
        val p = Porta(comparacao()); val vm = vm(p); advanceUntilIdle()
        assertEquals(Periodicidade.MENSAL, vm.estado.value.periodicidade); assertEquals("BASICO_20", vm.estado.value.planoEscolhido)
        vm.mudarPeriodicidade(Periodicidade.ANUAL); vm.escolherPlano("CONSULTORIO")
        assertEquals(AcaoPlano.ASSINAR, vm.estado.value.acao)
        vm.confirmar(); advanceUntilIdle()
        assertEquals(listOf("CONSULTORIO"), p.pedidos); assertEquals(listOf(Periodicidade.ANUAL), p.periodicidades)
        assertEquals("https://pagamento.exemplo/abc", vm.estado.value.urlCheckout)
    }

    @Test fun anualSoQuandoAlgumPlanoTemPrecoAnual() = runTest {
        val p = Porta(assinatura()); val vm = vm(p); advanceUntilIdle()
        vm.mudarPeriodicidade(Periodicidade.ANUAL); assertEquals(Periodicidade.MENSAL, vm.estado.value.periodicidade)
        vm.escolherPlano("NAO_EXISTE"); assertEquals("BASICO_20", vm.estado.value.planoEscolhido)
    }

    @Test fun ativaAbreNoPlanoAtualESemAcao() = runTest {
        val p = Porta(comparacao(StatusAssinatura.ATIVA, consultorio, Periodicidade.ANUAL)); val vm = vm(p); advanceUntilIdle()
        assertEquals(Periodicidade.ANUAL, vm.estado.value.periodicidade); assertEquals("CONSULTORIO", vm.estado.value.planoEscolhido)
        assertEquals(AcaoPlano.SEU_PLANO, vm.estado.value.acao)
        vm.confirmar(); advanceUntilIdle(); assertTrue(p.pedidos.isEmpty())
    }

    @Test fun descerAgendaATrocaERecarrega() = runTest {
        val p = Porta(comparacao(StatusAssinatura.ATIVA, consultorio, Periodicidade.MENSAL)); val vm = vm(p); advanceUntilIdle()
        p.url = null; p.agendada = LocalDate(2026, 12, 5)
        vm.escolherPlano("BASICO_20"); assertEquals(AcaoPlano.MUDAR, vm.estado.value.acao)
        p.atual = p.atual.copy(trocaAgendada = TrocaPlanoAgendadaDto("BASICO_20", Periodicidade.MENSAL, LocalDate(2026, 12, 5)))
        vm.confirmar(); advanceUntilIdle()
        assertNull(vm.estado.value.urlCheckout); assertEquals(LocalDate(2026, 12, 5), vm.estado.value.trocaAgendadaPara)
        assertNotNull(vm.estado.value.assinatura?.trocaAgendada)
        vm.escolherPlano("CONSULTORIO"); assertEquals(AcaoPlano.MANTER_ATUAL, vm.estado.value.acao); assertNull(vm.estado.value.trocaAgendadaPara)
    }

    @Test fun planoIndisponivelMostraMensagemERecarrega() = runTest {
        val p = Porta(comparacao()); val vm = vm(p); advanceUntilIdle()
        p.falha = ErroApi("PLANO_INDISPONIVEL", "RN-ASS-09", emptyList(), 422, null, null)
        vm.confirmar(); advanceUntilIdle()
        assertEquals(MensagemAssinatura.PLANO_INDISPONIVEL, vm.estado.value.erro)
    }

    @Test fun semChaveDoServidorNaoPedeCheckout() = runTest {
        val p = Porta(comparacao().copy(exibePrecoEBotao = false)); val vm = vm(p); advanceUntilIdle()
        assertEquals(AcaoPlano.INDISPONIVEL, vm.estado.value.acao)
        vm.confirmar(); advanceUntilIdle(); assertTrue(p.pedidos.isEmpty())
    }
}
