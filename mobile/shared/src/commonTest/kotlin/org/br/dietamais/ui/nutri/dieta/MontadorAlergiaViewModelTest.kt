package org.br.dietamais.ui.nutri.dieta

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalTime
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.*
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-83 (RN-DIE-14): alerta de intolerância e confirmação de alergia no montador. */
@OptIn(ExperimentalCoroutinesApi::class)
class MontadorAlergiaViewModelTest {
    private val paciente = Uuid.parse("019a0f32-5000-7000-8000-0000000000aa")
    private val agora = Instant.parse("2026-11-17T12:00:00Z")
    private val grupo = GrupoAlimentoResposta(Uuid.parse("019a0f32-5000-7000-8000-000000000001"), "Gorduras", 90.0, 3.0, 4.0, 7.0)
    private val pasta = AlimentoResposta(Uuid.parse("019a0f32-5000-7000-8000-000000000002"), null, grupo.id, "Pasta de amendoim", "1 colher de sopa", 15.0, true, 1, agora,
        marcadores = listOf("AMENDOIM"))
    private val iogurte = AlimentoResposta(Uuid.parse("019a0f32-5000-7000-8000-000000000003"), null, grupo.id, "Iogurte natural", "1 pote", 170.0, true, 1, agora,
        marcadores = listOf("CONTEM_LACTOSE"))
    private val banana = AlimentoResposta(Uuid.parse("019a0f32-5000-7000-8000-000000000004"), null, grupo.id, "Banana", "1 unidade", 80.0, true, 1, agora)
    private val alergia = RestricaoSaudeDto(Uuid.parse("019b0000-0000-7000-8000-000000000002"), TipoRestricaoSaude.ALERGIA, "Amendoim", GravidadeRestricao.GRAVE,
        marcadores = listOf("AMENDOIM"))
    private val intolerancia = RestricaoSaudeDto(Uuid.parse("019b0000-0000-7000-8000-000000000001"), TipoRestricaoSaude.INTOLERANCIA, "Lactose", GravidadeRestricao.MODERADA,
        marcadores = listOf("CONTEM_LACTOSE"))
    private val zero = TotaisNutricionaisResposta(0.0, 0.0, 0.0, 0.0)

    private inner class Dietas : PortaDietas {
        val criadas = mutableListOf<SalvarDietaRequisicao>(); val salvas = mutableListOf<SalvarDietaRequisicao>(); var publicadas = 0
        var falhaPublicar: Exception? = null
        private fun resposta(req: SalvarDietaRequisicao, id: Uuid, versao: Long) = DietaResposta(id, paciente, Uuid.random(), req.titulo, StatusDieta.RASCUNHO, req.orientacao,
            emptyList(), zero, versao, agora, agora)
        override suspend fun listar(pacienteId: Uuid) = emptyList<DietaResposta>()
        override suspend fun criar(pacienteId: Uuid, requisicao: SalvarDietaRequisicao, chave: Uuid): DietaResposta { criadas += requisicao; return resposta(requisicao, requisicao.id!!, 1) }
        override suspend fun salvar(id: Uuid, versao: Long, requisicao: SalvarDietaRequisicao): DietaResposta { salvas += requisicao; return resposta(requisicao, id, versao + 1) }
        override suspend fun publicar(id: Uuid, versao: Long): DietaSnapshotResposta { falhaPublicar?.let { throw it }; publicadas++; error("resposta não usada") }
        override suspend fun duplicar(id: Uuid, chave: Uuid) = error("não usado")
    }
    private val catalogo = object : PortaAlimentos {
        override fun observar() = flowOf(CatalogoAlimentos(listOf(grupo), listOf(banana, iogurte, pasta)))
        override suspend fun atualizar() {}
        override suspend fun criar(requisicao: CriarAlimentoRequisicao, chave: Uuid) = error("não usado")
        override suspend fun alterar(id: Uuid, versao: Long, requisicao: AtualizarAlimentoRequisicao) = error("não usado")
    }
    // sem rede: a regra roda no aparelho
    private fun TestScope.vm(d: Dietas, restricoes: List<RestricaoSaudeDto>? = listOf(intolerancia, alergia)) =
        MontadorDietaViewModel(d, catalogo, StandardTestDispatcher(testScheduler), conectividade = flowOf(false)).also { it.abrirPaciente(paciente, restricoes); runCurrent() }

    private fun MontadorDietaViewModel.novaCom(vararg alimentos: AlimentoResposta): Uuid {
        nova(); adicionarRefeicao(); mudarTitulo("Plano"); val ref = estado.value.rascunho!!.refeicoes.single().chave
        mudarNomeRefeicao(ref, "Lanche"); alimentos.forEach { adicionarItem(ref, it.id) }; return ref
    }
    private fun MontadorDietaViewModel.item(alimento: AlimentoResposta) = estado.value.rascunho!!.refeicoes.single().itens.first { it.alimentoId == alimento.id }

    @Test fun intoleranciaSoAvisaEAlergiaContaComoPendenteMesmoSemRede() = runTest {
        val vm = vm(Dietas()); vm.novaCom(iogurte, pasta, banana)
        val e = vm.estado.value
        assertTrue(e.semConexao)
        assertEquals(listOf(false), e.alertas.getValue(vm.item(iogurte).chave).map { it.exigeConfirmacao })
        assertEquals(GravidadeRestricao.GRAVE, e.alertas.getValue(vm.item(pasta).chave).single().gravidade)
        assertNull(e.alertas[vm.item(banana).chave])
        assertEquals(1, e.alergiasSemConfirmacao)
    }

    @Test fun alergiaBloqueiaPublicarAteConfirmarEAConfirmacaoVaiNoItem() = runTest {
        val d = Dietas()
        val vm = MontadorDietaViewModel(d, catalogo, StandardTestDispatcher(testScheduler)).also { it.abrirPaciente(paciente, listOf(alergia)); runCurrent() }
        val ref = vm.novaCom(pasta); vm.salvar(); runCurrent()
        assertNull(d.criadas.single().refeicoes.single().itens.single().alergiaConfirmada)
        vm.pedirPublicacao()
        assertFalse(vm.estado.value.confirmarPublicar); assertEquals(MensagemDieta.ALERGIA_NAO_CONFIRMADA, vm.estado.value.erro)

        val chave = vm.item(pasta).chave
        vm.revisarAlergia(ref, chave); assertEquals(ref to chave, vm.estado.value.revisarAlergia)
        vm.cancelarRevisaoAlergia(); assertNull(vm.estado.value.revisarAlergia); assertFalse(vm.item(pasta).alergiaConfirmada)
        vm.revisarAlergia(ref, chave); vm.confirmarAlergia()
        assertTrue(vm.item(pasta).alergiaConfirmada); assertEquals(0, vm.estado.value.alergiasSemConfirmacao); assertTrue(vm.estado.value.rascunho!!.alterado)
        vm.salvar(); runCurrent()
        assertEquals(true, d.salvas.single().refeicoes.single().itens.single().alergiaConfirmada)
        vm.pedirPublicacao(); assertTrue(vm.estado.value.confirmarPublicar)

        vm.cancelarPublicacao(); vm.desfazerConfirmacao(ref, chave)
        assertFalse(vm.item(pasta).alergiaConfirmada); assertEquals(1, vm.estado.value.alergiasSemConfirmacao)
    }

    @Test fun revisarSoAbreParaItemComAlergia() = runTest {
        val vm = vm(Dietas()); val ref = vm.novaCom(iogurte)
        vm.revisarAlergia(ref, vm.item(iogurte).chave); assertNull(vm.estado.value.revisarAlergia)
    }

    @Test fun trocarAlimentoTiraOItemEAbreABusca() = runTest {
        val vm = vm(Dietas()); val ref = vm.novaCom(pasta, banana)
        val chave = vm.item(pasta).chave
        vm.revisarAlergia(ref, chave); vm.trocarAlimento(ref, chave)
        assertEquals(listOf(banana.id), vm.estado.value.rascunho!!.refeicoes.single().itens.map { it.alimentoId })
        assertEquals(ref, vm.estado.value.seletorRefeicao); assertNull(vm.estado.value.revisarAlergia); assertEquals(0, vm.estado.value.alergiasSemConfirmacao)
    }

    @Test fun restricaoNovaNaFichaRecalculaSemTrocarDePaciente() = runTest {
        val vm = vm(Dietas(), restricoes = emptyList()); vm.novaCom(pasta)
        assertEquals(0, vm.estado.value.alergiasSemConfirmacao)
        vm.abrirPaciente(paciente, listOf(alergia))
        assertEquals(1, vm.estado.value.alergiasSemConfirmacao); assertNotNull(vm.estado.value.rascunho)
    }

    @Test fun semAFichaValemOsAlertasDoServidorEAConfirmacaoSalva() = runTest {
        val vm = vm(Dietas(), restricoes = null)
        fun item(confirmada: Boolean?) = ItemRefeicaoResposta(Uuid.random(), alimentoId = pasta.id, porcoes = 1.0, totais = zero,
            alertas = listOf(AlertaRestricaoDto(alergia.id, TipoRestricaoSaude.ALERGIA, "Amendoim", "AMENDOIM", true)), alergiaConfirmada = confirmada)
        fun dieta(confirmada: Boolean?) = DietaResposta(Uuid.random(), paciente, Uuid.random(), "Plano", StatusDieta.RASCUNHO, null,
            listOf(RefeicaoResposta(Uuid.random(), "Lanche", LocalTime(16, 0), 0, listOf(item(confirmada)), zero)), zero, 3, agora, agora)
        vm.abrir(dieta(true))
        assertTrue(vm.item(pasta).alergiaConfirmada); assertEquals(1, vm.estado.value.alertas.size); assertEquals(0, vm.estado.value.alergiasSemConfirmacao)
        vm.abrir(dieta(null)); assertEquals(1, vm.estado.value.alergiasSemConfirmacao)
    }

    @Test fun publicarDaListaComAlergiaSemConfirmacaoPedeParaAbrir() = runTest {
        val vm = MontadorDietaViewModel(Dietas(), catalogo, StandardTestDispatcher(testScheduler)).also { it.abrirPaciente(paciente, listOf(alergia)); runCurrent() }
        val item = ItemRefeicaoResposta(Uuid.random(), alimentoId = pasta.id, porcoes = 1.0, totais = zero,
            alertas = listOf(AlertaRestricaoDto(alergia.id, TipoRestricaoSaude.ALERGIA, "Amendoim", "AMENDOIM", true)))
        val dieta = DietaResposta(Uuid.random(), paciente, Uuid.random(), "Plano", StatusDieta.RASCUNHO, null,
            listOf(RefeicaoResposta(Uuid.random(), "Lanche", LocalTime(16, 0), 0, listOf(item), zero)), zero, 3, agora, agora)
        vm.pedirPublicacaoDaLista(dieta)
        assertNull(vm.estado.value.publicarDaLista); assertEquals(MensagemDieta.ALERGIA_NAO_CONFIRMADA, vm.estado.value.erro)
    }

    @Test fun recusaDoServidorAoPublicarViraMensagem() = runTest {
        val d = Dietas().apply { falhaPublicar = ErroApi("ALERGIA_NAO_CONFIRMADA", "RN-DIE-14", emptyList(), 422, null, null) }
        // a ficha local ainda não tem a alergia que o servidor conhece
        val vm = MontadorDietaViewModel(d, catalogo, StandardTestDispatcher(testScheduler)).also { it.abrirPaciente(paciente, emptyList()); runCurrent() }
        vm.novaCom(pasta); vm.salvar(); runCurrent()
        vm.pedirPublicacao(); assertTrue(vm.estado.value.confirmarPublicar)
        vm.confirmarPublicacao(); runCurrent()
        assertEquals(MensagemDieta.ALERGIA_NAO_CONFIRMADA, vm.estado.value.erro); assertFalse(vm.estado.value.confirmarPublicar)
    }
}
