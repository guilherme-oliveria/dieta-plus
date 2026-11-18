package org.br.dietamais.ui.paciente.dieta

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.OrientacaoResposta
import org.br.dietamais.data.remoto.dto.SuplementoResposta
import org.br.dietamais.data.repositorio.PortaPrescricoesPaciente
import org.br.dietamais.dominio.prescricoes.SecaoPrescricaoPaciente
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class PrescricoesPacienteViewModelTest {
    private val t = Instant.parse("2026-11-01T00:00:00Z")
    private val agora = Instant.parse("2026-11-18T12:00:00Z")
    private val relogio = object : Clock { override fun now() = agora }

    private class Porta : PortaPrescricoesPaciente {
        val suplementos = MutableStateFlow<List<SuplementoResposta>>(emptyList())
        val orientacoes = MutableStateFlow<List<OrientacaoResposta>>(emptyList())
        val sem = MutableStateFlow<Set<Uuid>>(emptySet()); val vistas = MutableStateFlow<Set<Uuid>>(emptySet())
        var falhaLembrete = false
        override fun observarSuplementos() = suplementos
        override fun observarOrientacoes() = orientacoes
        override fun observarSemLembrete() = sem
        override fun definirLembrete(suplemento: Uuid, ligado: Boolean) { if (falhaLembrete) error("falhou"); sem.value = if (ligado) sem.value - suplemento else sem.value + suplemento }
        override fun observarVistas() = vistas
        override fun marcarVistas(ids: Collection<Uuid>) { vistas.value = vistas.value + ids }
    }

    private fun sup(nome: String, horarios: List<String> = listOf("16:00"), fim: LocalDate? = null) =
        SuplementoResposta(Uuid.random(), Uuid.random(), nome = nome, dose = "1", horarios = horarios, inicio = LocalDate(2026, 11, 1), fim = fim, versao = 1, criadoEm = t, atualizadoEm = t)
    private fun ori(titulo: String, dia: Int) = OrientacaoResposta(Uuid.random(), Uuid.random(), titulo, "x", null, 1, Instant.parse("2026-11-${dia}T10:00:00Z"), t)

    private fun TestScope.vm(p: Porta) = PrescricoesPacienteViewModel(p, relogio, TimeZone.UTC, StandardTestDispatcher(testScheduler), flowOf(Unit))

    @Test fun leDoBancoLocalSoOsVigentesComAProximaTomada() = runTest {
        val p = Porta(); val c = sup("Creatina"); p.suplementos.value = listOf(c, sup("Acabou", fim = LocalDate(2026, 11, 10)))
        val vm = vm(p); advanceUntilIdle()
        val e = vm.estado.value
        assertTrue(e.carregado); assertTrue(e.temAlgo)
        assertEquals(listOf("Creatina"), e.suplementos.map { it.nome })
        assertEquals(Instant.parse("2026-11-18T16:00:00Z"), e.proximas[c.id])
    }

    @Test fun abrirOrientacoesMarcaComoVistasEMantemOSelo() = runTest {
        val p = Porta(); val nova = ori("Prato", 18); val velha = ori("Água", 10)
        p.orientacoes.value = listOf(velha, nova); p.vistas.value = setOf(velha.id)
        val vm = vm(p); advanceUntilIdle()
        assertEquals(setOf(nova.id), vm.estado.value.novas)
        assertEquals(listOf("Prato", "Água"), vm.estado.value.orientacoes.map { it.titulo })
        vm.abrir(SecaoPrescricaoPaciente.ORIENTACOES); advanceUntilIdle()
        assertEquals(setOf(velha.id, nova.id), p.vistas.value)
        assertTrue(vm.estado.value.novas.isEmpty())
        assertEquals(setOf(nova.id), vm.estado.value.destacadas)
        // chega outra pelo sync com a tela aberta
        val outra = ori("Rótulo", 18).copy(criadoEm = Instant.parse("2026-11-18T11:00:00Z"))
        p.orientacoes.value = p.orientacoes.value + outra; advanceUntilIdle()
        assertTrue(outra.id in p.vistas.value); assertTrue(outra.id in vm.estado.value.destacadas)
        vm.fechar()
        assertNull(vm.estado.value.aberta); assertTrue(vm.estado.value.destacadas.isEmpty())
    }

    @Test fun alternarOrientacaoAbreERecolhe() = runTest {
        val p = Porta(); val o = ori("Água", 10); p.orientacoes.value = listOf(o)
        val vm = vm(p); advanceUntilIdle()
        vm.alternarOrientacao(o.id); assertEquals(o.id, vm.estado.value.orientacaoAberta)
        vm.alternarOrientacao(o.id); assertNull(vm.estado.value.orientacaoAberta)
    }

    @Test fun desligarLembreteGuardaNoAparelhoEDesfazSeFalhar() = runTest {
        val p = Porta(); val c = sup("Creatina"); p.suplementos.value = listOf(c)
        val vm = vm(p); advanceUntilIdle()
        vm.mudarLembrete(c.id, false); advanceUntilIdle()
        assertEquals(setOf(c.id), p.sem.value); assertEquals(setOf(c.id), vm.estado.value.semLembrete)
        p.falhaLembrete = true
        vm.mudarLembrete(c.id, true)
        assertEquals(setOf(c.id), vm.estado.value.semLembrete)
    }

    @Test fun falhaNaLeituraMostraErro() = runTest {
        val p = object : PortaPrescricoesPaciente by Porta() { override fun observarSuplementos(): Flow<List<SuplementoResposta>> = flow { error("json") } }
        val vm = PrescricoesPacienteViewModel(p, relogio, TimeZone.UTC, StandardTestDispatcher(testScheduler), flowOf(Unit)); advanceUntilIdle()
        assertTrue(vm.estado.value.erro); assertTrue(vm.estado.value.carregado)
    }
}
