package org.br.dietamais.ui.nutri.metas

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.ErroApi
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.PortaMetasNutri
import org.br.dietamais.dominio.metas.ErroMeta
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MetasNutriViewModelTest {
    private val hoje = LocalDate(2026, 11, 17)
    private val paciente = Uuid.random()
    private val t = Instant.parse("2026-11-01T10:00:00Z")

    private inner class Porta : PortaMetasNutri {
        val metas = mutableListOf(MetaResposta(Uuid.random(), paciente, TipoMeta.AGUA, null, 2100.0, null, FrequenciaMeta.DIARIA, alvoPadrao = true, versao = 1, criadoEm = t, atualizadoEm = t))
        val modelos = mutableListOf<ModeloMetaResposta>()
        val criadas = mutableListOf<MetaRequisicao>(); val atualizacoes = mutableListOf<Triple<Uuid, Long, AtualizarMetaRequisicao>>()
        val aplicados = mutableListOf<Uuid>(); val excluidos = mutableListOf<Pair<Uuid, Long>>()
        var falha: Exception? = null; var leituras = 0
        override suspend fun metas(pacienteId: Uuid): List<MetaResposta> { leituras++; return metas.toList() }
        override suspend fun cumprimento(pacienteId: Uuid) = CumprimentoMetasResposta(LocalDate(2026, 11, 10), hoje,
            metas.map { CumprimentoMeta(it.metaId(), it.tipo, it.nome, 7, 3, 42.9, 1, 3) })
        private fun MetaResposta.metaId() = id
        override suspend fun criar(pacienteId: Uuid, requisicao: MetaRequisicao): MetaResposta {
            falha?.let { throw it }; criadas += requisicao
            return MetaResposta(requisicao.id!!, pacienteId, requisicao.tipo, requisicao.nome, requisicao.alvo, requisicao.unidade, requisicao.frequencia,
                requisicao.vezesPorSemana, requisicao.inicio, requisicao.fim, null, 1, t, t).also { metas += it }
        }
        override suspend fun atualizar(metaId: Uuid, versao: Long, requisicao: AtualizarMetaRequisicao): MetaResposta {
            falha?.let { throw it }; atualizacoes += Triple(metaId, versao, requisicao)
            val i = metas.indexOfFirst { it.id == metaId }
            return metas[i].copy(alvo = requisicao.alvo ?: metas[i].alvo, versao = versao + 1, fim = if (requisicao.encerrar == true) hoje.minusUmDia() else metas[i].fim)
                .also { metas[i] = it }
        }
        override suspend fun modelos() = modelos.toList()
        override suspend fun criarModelo(requisicao: ModeloMetaRequisicao) = ModeloMetaResposta(Uuid.random(), requisicao.nome, requisicao.itens, 1, t, t).also { modelos += it }
        override suspend fun excluirModelo(id: Uuid, versao: Long) { excluidos += id to versao; modelos.removeAll { it.id == id } }
        override suspend fun aplicarModelo(pacienteId: Uuid, modeloId: Uuid): List<MetaResposta> { aplicados += modeloId; return emptyList() }
    }
    private fun LocalDate.minusUmDia() = LocalDate.fromEpochDays(toEpochDays() - 1)

    private fun TestScope.vm(porta: Porta, online: MutableStateFlow<Boolean>? = null) =
        MetasNutriViewModel(porta, StandardTestDispatcher(testScheduler), online, { hoje })

    @Test fun abreComMetasECumprimento() = runTest {
        val porta = Porta(); val vm = vm(porta)
        vm.abrir(paciente); advanceUntilIdle(); vm.abrir(paciente); advanceUntilIdle()
        val e = vm.estado.value
        assertTrue(e.carregado); assertEquals(1, porta.leituras); assertEquals(TipoMeta.AGUA, e.ativas().single().tipo)
        assertEquals(42.9, e.cumprimentoDe(e.metas.single().id)?.percentual)
    }

    @Test fun novaMetaValidaECriaComIdDoApp() = runTest {
        val porta = Porta(); val vm = vm(porta); vm.abrir(paciente); advanceUntilIdle()
        vm.novaMeta(); assertEquals("17/11/2026", vm.estado.value.formulario?.inicio)
        vm.mudarTipo(TipoMeta.AGUA); assertEquals(TipoMeta.ATIVIDADE_MIN, vm.estado.value.formulario?.tipo) // água não se cria
        vm.salvarMeta(); assertEquals(setOf(ErroMeta.ALVO), vm.estado.value.errosFormulario); assertTrue(porta.criadas.isEmpty())
        vm.mudarFrequencia(FrequenciaMeta.SEMANAL); vm.mudarVezes(10); vm.mudarAlvo("30")
        assertEquals(7, vm.estado.value.formulario?.vezesPorSemana)
        vm.salvarMeta(); advanceUntilIdle()
        val r = porta.criadas.single()
        assertEquals(7, r.id!!.toByteArray()[6].toInt() shr 4 and 0xF) // UUIDv7
        assertEquals(30.0, r.alvo); assertEquals(7, r.vezesPorSemana); assertEquals(hoje, r.inicio)
        assertNull(vm.estado.value.formulario); assertEquals(MensagemMetas.META_SALVA, vm.estado.value.aviso); assertEquals(2, vm.estado.value.metas.size)
    }

    @Test fun editarAguaMandaSoOAlvoComAVersaoENaoEncerra() = runTest {
        val porta = Porta(); val vm = vm(porta); vm.abrir(paciente); advanceUntilIdle()
        val agua = vm.estado.value.metas.single()
        vm.editar(agua.id); vm.mudarAlvo("2500"); vm.mudarInicio("lixo") // início não vale para a água
        vm.encerrarMeta(); advanceUntilIdle(); assertTrue(porta.atualizacoes.isEmpty())
        vm.salvarMeta(); advanceUntilIdle()
        assertEquals(Triple(agua.id, 1L, AtualizarMetaRequisicao(alvo = 2500.0)), porta.atualizacoes.single())
        assertEquals(2500.0, vm.estado.value.metas.single().alvo)
    }

    @Test fun encerrarTiraAMetaDasAtivas() = runTest {
        val porta = Porta(); val vm = vm(porta); vm.abrir(paciente); advanceUntilIdle()
        vm.novaMeta(); vm.mudarTipo(TipoMeta.PASSOS); vm.mudarAlvo("7000"); vm.salvarMeta(); advanceUntilIdle()
        val passos = vm.estado.value.metas.first { it.tipo == TipoMeta.PASSOS }
        vm.editar(passos.id); vm.encerrarMeta(); advanceUntilIdle()
        assertEquals(AtualizarMetaRequisicao(encerrar = true), porta.atualizacoes.single().third)
        assertEquals(listOf(TipoMeta.AGUA), vm.estado.value.ativas().map { it.tipo }); assertEquals(MensagemMetas.META_ENCERRADA, vm.estado.value.aviso)
    }

    @Test fun conflitoRecarregaEMantemOFormularioNaVersaoNova() = runTest {
        val porta = Porta(); val vm = vm(porta); vm.abrir(paciente); advanceUntilIdle()
        val agua = vm.estado.value.metas.single()
        vm.editar(agua.id); vm.mudarAlvo("2500")
        porta.metas[0] = agua.copy(versao = 4)
        porta.falha = ErroApi("CONFLITO_VERSAO", null, emptyList(), 409, null, null)
        vm.salvarMeta(); advanceUntilIdle()
        assertEquals(MensagemMetas.CONFLITO, vm.estado.value.erro)
        assertEquals(4L, vm.estado.value.formulario?.versao); assertEquals("2500", vm.estado.value.formulario?.alvo)
        porta.falha = ErroApi("META_INVALIDA", "RN-MET-01", emptyList(), 422, null, null)
        vm.salvarMeta(); advanceUntilIdle(); assertEquals(MensagemMetas.META_INVALIDA, vm.estado.value.erro)
    }

    @Test fun modelosAplicarSalvarEExcluir() = runTest {
        val porta = Porta(); val vm = vm(porta); vm.abrir(paciente); advanceUntilIdle()
        vm.abrirModelos(); advanceUntilIdle(); assertEquals(emptyList(), vm.estado.value.modelos)
        vm.iniciarNovoModelo(); vm.mudarNomeModelo("Começo"); vm.salvarModelo(); advanceUntilIdle()
        assertTrue(vm.estado.value.modelos!!.isEmpty()) // só água: nada para salvar
        vm.fecharModelos()
        vm.novaMeta(); vm.mudarTipo(TipoMeta.SIM_NAO); vm.mudarNome("Sem refrigerante"); vm.salvarMeta(); advanceUntilIdle()
        vm.abrirModelos(); advanceUntilIdle(); vm.iniciarNovoModelo(); vm.mudarNomeModelo("Começo"); vm.salvarModelo(); advanceUntilIdle()
        val modelo = vm.estado.value.modelos!!.single()
        assertEquals(listOf(TipoMeta.SIM_NAO), modelo.itens.map { it.tipo }); assertEquals(MensagemMetas.MODELO_SALVO, vm.estado.value.aviso)
        vm.aplicarModelo(modelo.id); advanceUntilIdle()
        assertEquals(listOf(modelo.id), porta.aplicados); assertFalse(vm.estado.value.modelosAbertos)
        vm.abrirModelos(); advanceUntilIdle(); vm.excluirModelo(modelo.id); advanceUntilIdle()
        assertEquals(listOf(modelo.id to 1L), porta.excluidos); assertTrue(vm.estado.value.modelos!!.isEmpty())
    }

    @Test fun semRedeNaoChamaEBuscaQuandoVolta() = runTest {
        val porta = Porta(); val online = MutableStateFlow(false); val vm = vm(porta, online); advanceUntilIdle()
        vm.abrir(paciente); advanceUntilIdle()
        assertEquals(MensagemMetas.SEM_CONEXAO, vm.estado.value.erro); assertEquals(0, porta.leituras)
        online.value = true; advanceUntilIdle()
        assertTrue(vm.estado.value.carregado); assertNull(vm.estado.value.erro)
    }
}
