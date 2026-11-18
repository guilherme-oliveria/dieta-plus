package org.br.dietamais.ui.nutri.prescricoes

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.ErroApi
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.PortaPrescricoesNutri
import org.br.dietamais.data.repositorio.PortaProdutos
import org.br.dietamais.dominio.prescricoes.*
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class PrescricoesNutriViewModelTest {
    private val hoje = LocalDate(2026, 11, 17)
    private val paciente = Uuid.random()
    private val t = Instant.parse("2026-11-01T10:00:00Z")
    private val creatina = ProdutoResposta(Uuid.random(), "Creatina", "Marca X", versao = 1, atualizadoEm = t)

    private inner class Porta : PortaPrescricoesNutri {
        val suplementos = mutableListOf<SuplementoResposta>(); val orientacoes = mutableListOf<OrientacaoResposta>()
        val modelos = mutableListOf<ModeloOrientacaoResposta>()
        val prescritos = mutableListOf<SuplementoRequisicao>(); val atualizados = mutableListOf<Pair<Long, AtualizarSuplementoRequisicao>>()
        val enviadas = mutableListOf<OrientacaoRequisicao>(); val arquivadas = mutableListOf<Uuid>(); var falha: Exception? = null
        override suspend fun suplementos(pacienteId: Uuid) = suplementos.toList()
        override suspend fun prescrever(pacienteId: Uuid, requisicao: SuplementoRequisicao): SuplementoResposta {
            falha?.let { throw it }; prescritos += requisicao
            return SuplementoResposta(requisicao.id!!, pacienteId, requisicao.produtoId, requisicao.nome ?: "Creatina", requisicao.dose, requisicao.forma,
                requisicao.frequencia, requisicao.horarios.orEmpty(), requisicao.inicio!!, requisicao.fim, 1, t, t).also { suplementos += it }
        }
        override suspend fun atualizarSuplemento(id: Uuid, versao: Long, requisicao: AtualizarSuplementoRequisicao): SuplementoResposta {
            falha?.let { throw it }; atualizados += versao to requisicao
            val i = suplementos.indexOfFirst { it.id == id }
            return suplementos[i].copy(dose = requisicao.dose ?: suplementos[i].dose, versao = versao + 1,
                fim = if (requisicao.encerrar == true) LocalDate(2026, 11, 16) else suplementos[i].fim).also { suplementos[i] = it }
        }
        override suspend fun orientacoes(pacienteId: Uuid) = orientacoes.toList()
        override suspend fun enviarOrientacao(pacienteId: Uuid, requisicao: OrientacaoRequisicao): OrientacaoResposta {
            falha?.let { throw it }; enviadas += requisicao
            return OrientacaoResposta(requisicao.id!!, pacienteId, requisicao.titulo, requisicao.texto, null, 1, t, t).also { orientacoes += it }
        }
        override suspend fun atualizarOrientacao(id: Uuid, versao: Long, requisicao: AtualizarOrientacaoRequisicao): OrientacaoResposta {
            arquivadas += id; val i = orientacoes.indexOfFirst { it.id == id }
            return orientacoes[i].copy(arquivadaEm = t, versao = versao + 1).also { orientacoes[i] = it }
        }
        override suspend fun modelosOrientacao() = modelos.toList()
        override suspend fun criarModeloOrientacao(requisicao: ModeloOrientacaoRequisicao) =
            ModeloOrientacaoResposta(Uuid.random(), requisicao.titulo, requisicao.texto, 1, t, t).also { modelos += it }
        override suspend fun excluirModeloOrientacao(id: Uuid, versao: Long) { modelos.removeAll { it.id == id } }
    }
    private class Produtos(val lista: List<ProdutoResposta>) : PortaProdutos {
        var buscas = 0
        override suspend fun listar(busca: String, ativo: Boolean, pagina: Int): PaginaProdutosResposta { buscas++; return PaginaProdutosResposta(lista, 0, 20, lista.size.toLong()) }
        override suspend fun criar(requisicao: CriarProdutoRequisicao, chave: Uuid) = error("não usado")
        override suspend fun alterar(id: Uuid, versao: Long, requisicao: AtualizarProdutoRequisicao) = error("não usado")
    }

    private fun TestScope.vm(porta: Porta, produtos: Produtos? = null, online: MutableStateFlow<Boolean>? = null) =
        PrescricoesNutriViewModel(porta, produtos, StandardTestDispatcher(testScheduler), online, { hoje })

    @Test fun prescreveProdutoDaNutriComBusca() = runTest {
        val porta = Porta(); val produtos = Produtos(listOf(creatina)); val vm = vm(porta, produtos); vm.abrir(paciente); advanceUntilIdle()
        vm.prescrever(); assertEquals("Todo dia", vm.estado.value.suplemento?.frequencia)
        vm.mudarDose("3 g"); vm.salvarSuplemento()
        assertEquals(setOf(ErroSuplemento.PRODUTO), vm.estado.value.errosSuplemento)
        vm.buscarProduto("cre"); vm.buscarProduto("crea"); advanceUntilIdle()
        assertEquals(1, produtos.buscas); assertEquals(listOf(creatina), vm.estado.value.produtos)
        vm.escolherProduto(creatina); vm.mudarForma("Pó"); vm.mudarHorarios("16:00")
        vm.salvarSuplemento(); advanceUntilIdle()
        val r = porta.prescritos.single()
        assertEquals(creatina.id, r.produtoId); assertNull(r.nome); assertEquals("Pó", r.forma); assertEquals(listOf("16:00"), r.horarios); assertEquals(hoje, r.inicio)
        assertNull(vm.estado.value.suplemento); assertEquals(1, vm.estado.value.emUso().size)
    }

    @Test fun editarMudaDoseEEncerrarTiraDoEmUso() = runTest {
        val porta = Porta(); val vm = vm(porta); vm.abrir(paciente); advanceUntilIdle()
        vm.prescrever(); vm.mudarOrigem(OrigemSuplemento.TEXTO_LIVRE); vm.mudarNome("Ômega 3"); vm.mudarDose("2 cápsulas"); vm.salvarSuplemento(); advanceUntilIdle()
        val s = vm.estado.value.suplementos.single()
        vm.editarSuplemento(s.id); vm.mudarNome("outro"); assertEquals("Ômega 3", vm.estado.value.suplemento?.nome) // nome congelado
        vm.mudarDose("1 cápsula"); vm.salvarSuplemento(); advanceUntilIdle()
        assertEquals(1L to "1 cápsula", porta.atualizados.single().let { it.first to it.second.dose })
        vm.editarSuplemento(s.id); vm.encerrarSuplemento(); advanceUntilIdle()
        assertEquals(true, porta.atualizados.last().second.encerrar); assertEquals(2L, porta.atualizados.last().first)
        assertTrue(vm.estado.value.emUso().isEmpty()); assertEquals(1, vm.estado.value.encerrados().size)
    }

    @Test fun enviaOrientacaoComModeloEArquiva() = runTest {
        val porta = Porta(); val vm = vm(porta); vm.abrir(paciente); advanceUntilIdle()
        vm.mudarSecao(SecaoPrescricoes.ORIENTACOES); vm.novaOrientacao(); vm.enviarOrientacao()
        assertEquals(setOf(ErroOrientacao.TITULO, ErroOrientacao.TEXTO), vm.estado.value.errosOrientacao)
        vm.mudarTitulo("Como montar o prato"); vm.mudarTexto("Metade"); vm.negrito(); vm.itemLista(); vm.alternarSalvarModelo()
        assertEquals("Metade **negrito**\n- ", vm.estado.value.orientacao?.texto)
        vm.enviarOrientacao(); advanceUntilIdle()
        assertEquals("Como montar o prato", porta.enviadas.single().titulo); assertEquals(1, porta.modelos.size)
        assertEquals(MensagemPrescricoes.ORIENTACAO_ENVIADA, vm.estado.value.aviso); assertNull(vm.estado.value.orientacao)
        val o = vm.estado.value.orientacoes.single()
        vm.arquivarOrientacao(o.id); advanceUntilIdle()
        assertEquals(listOf(o.id), porta.arquivadas); assertNotNull(vm.estado.value.orientacoes.single().arquivadaEm)
        vm.arquivarOrientacao(o.id); advanceUntilIdle(); assertEquals(1, porta.arquivadas.size)
    }

    @Test fun usarModeloPreencheEExcluirTira() = runTest {
        val porta = Porta(); porta.modelos += ModeloOrientacaoResposta(Uuid.random(), "Hidratação", "Beba água", 3, t, t)
        val vm = vm(porta); vm.abrir(paciente); advanceUntilIdle()
        vm.novaOrientacao(); vm.usarModelo(); advanceUntilIdle()
        assertTrue(vm.estado.value.escolhendoModelo)
        vm.escolherModelo(porta.modelos.single().id)
        assertEquals(FormularioOrientacao("Hidratação", "Beba água"), vm.estado.value.orientacao); assertFalse(vm.estado.value.escolhendoModelo)
        vm.usarModelo(); vm.excluirModelo(porta.modelos.single().id); advanceUntilIdle()
        assertEquals(emptyList(), vm.estado.value.modelos)
    }

    @Test fun errosDoServidorViramMensagem() = runTest {
        val porta = Porta(); val vm = vm(porta); vm.abrir(paciente); advanceUntilIdle()
        porta.falha = ErroApi("ACESSO_NEGADO", "RN-VIN-05", emptyList(), 403, null, null)
        vm.prescrever(); vm.mudarOrigem(OrigemSuplemento.TEXTO_LIVRE); vm.mudarNome("Ômega 3"); vm.mudarDose("1"); vm.salvarSuplemento(); advanceUntilIdle()
        assertEquals(MensagemPrescricoes.VINCULO_ENCERRADO, vm.estado.value.erro); assertNotNull(vm.estado.value.suplemento)
        porta.falha = ErroApi("ITEM_INVALIDO", "RN-SUP-01", emptyList(), 422, null, null)
        vm.salvarSuplemento(); advanceUntilIdle(); assertEquals(MensagemPrescricoes.INVALIDO, vm.estado.value.erro)
    }

    @Test fun semRedeNaoChama() = runTest {
        val porta = Porta(); val online = MutableStateFlow(false); val vm = vm(porta, online = online); advanceUntilIdle()
        vm.abrir(paciente); advanceUntilIdle()
        assertEquals(MensagemPrescricoes.SEM_CONEXAO, vm.estado.value.erro); assertFalse(vm.estado.value.carregado)
        online.value = true; advanceUntilIdle(); assertTrue(vm.estado.value.carregado)
    }
}
