package org.br.dietamais.ui.paciente.metas

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.DadosMetasPaciente
import org.br.dietamais.data.repositorio.PortaMetasPaciente
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MetasPacienteViewModelTest {
    // terça, 17/11/2026, 9h em UTC
    private val relogio = object : Clock { override fun now() = Instant.parse("2026-11-17T09:00:00Z") }
    private val hoje = LocalDate(2026, 11, 17)
    private val criada = Instant.parse("2026-11-01T00:00:00Z")

    private fun meta(tipo: TipoMeta, alvo: Double = 30.0, frequencia: FrequenciaMeta = FrequenciaMeta.DIARIA, vezes: Int? = null,
        inicio: LocalDate? = null, fim: LocalDate? = null, nome: String? = null) =
        MetaResposta(Uuid.random(), Uuid.random(), tipo, nome, alvo, null, frequencia, vezes, inicio, fim, versao = 1, criadoEm = criada, atualizadoEm = criada)

    private class Porta(inicial: DadosMetasPaciente) : PortaMetasPaciente {
        val dados = MutableStateFlow(inicial)
        val gravados = mutableListOf<RegistroMetaResposta>()
        var falha = false
        val recusas = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        override val falhas: Flow<Unit> = recusas
        override fun observar() = dados
        val lembretes = MutableStateFlow<Map<Uuid, List<LocalTime>>>(emptyMap())
        override fun observarLembretes() = lembretes
        override fun definirLembrete(metaId: Uuid, horarios: List<LocalTime>) {
            if (falha) throw IllegalStateException("falha simulada")
            lembretes.value = if (horarios.isEmpty()) lembretes.value - metaId else lembretes.value + (metaId to horarios)
        }
        override fun registrar(metaId: Uuid, data: LocalDate, valor: Double, agora: Instant) {
            if (falha) throw IllegalStateException("falha simulada")
            val r = RegistroMetaResposta(Uuid.random(), metaId, data, valor, agora)
            gravados += r; dados.value = dados.value.copy(registros = dados.value.registros + r)
        }
    }

    private fun TestScope.criar(porta: Porta, aoGravar: () -> Unit = {}) =
        MetasPacienteViewModel(porta, relogio, TimeZone.UTC, UnconfinedTestDispatcher(testScheduler), flowOf(Unit), aoGravar)

    @Test fun aguaFicaForaEMetaForaDoPeriodoSaiDoHojeMasFicaNaSemana() = runTest {
        val agua = meta(TipoMeta.AGUA, 2000.0)
        val caminhada = meta(TipoMeta.ATIVIDADE_MIN)
        val acabou = meta(TipoMeta.PASSOS, 8000.0, fim = LocalDate(2026, 11, 16))
        val antiga = meta(TipoMeta.FRUTAS_VERDURAS, 5.0, fim = LocalDate(2026, 11, 10))
        val vm = criar(Porta(DadosMetasPaciente(listOf(agua, caminhada, acabou, antiga))))
        val e = vm.estado.value
        assertTrue(e.carregado); assertEquals(hoje, e.hojeData)
        assertEquals(listOf(caminhada.id), e.hoje.map { it.meta.id })
        assertEquals(listOf(caminhada.id, acabou.id), e.semana.map { it.meta.id })
    }

    @Test fun somarGravaHojeEAtualizaOProgresso() = runTest {
        val caminhada = meta(TipoMeta.ATIVIDADE_MIN)
        val porta = Porta(DadosMetasPaciente(listOf(caminhada)))
        var gravou = 0
        val vm = criar(porta) { gravou++ }
        vm.somar(caminhada.id, 10.0); vm.somar(caminhada.id, 20.0)
        assertEquals(listOf(10.0, 20.0), porta.gravados.map { it.valor })
        assertTrue(porta.gravados.all { it.data == hoje })
        assertEquals(2, gravou)
        val p = vm.estado.value.hoje.single()
        assertEquals(30.0, p.hoje); assertTrue(p.cumpridaHoje)
        // valor fora da faixa e meta desconhecida não gravam
        vm.somar(caminhada.id, 0.0); vm.somar(Uuid.random(), 5.0)
        assertEquals(2, porta.gravados.size)
    }

    @Test fun simNaoGravaUmaVezSoNoDia() = runTest {
        val sem = meta(TipoMeta.SIM_NAO, 1.0, nome = "Sem refrigerante")
        val porta = Porta(DadosMetasPaciente(listOf(sem)))
        val vm = criar(porta)
        vm.cumpriHoje(sem.id); vm.cumpriHoje(sem.id)
        assertEquals(listOf(1.0), porta.gravados.map { it.valor })
        assertTrue(vm.estado.value.hoje.single().cumpridaHoje)
    }

    @Test fun outroValorValidaEGrava() = runTest {
        val frutas = meta(TipoMeta.FRUTAS_VERDURAS, 5.0)
        val porta = Porta(DadosMetasPaciente(listOf(frutas)))
        val vm = criar(porta)
        vm.abrirOutro(frutas.id)
        assertEquals(frutas.id, vm.estado.value.outroValor)
        vm.mudarTextoOutro("abc"); vm.confirmarOutro()
        assertTrue(vm.estado.value.erroOutro); assertTrue(porta.gravados.isEmpty())
        vm.mudarTextoOutro("2,5"); vm.confirmarOutro()
        assertEquals(listOf(2.5), porta.gravados.map { it.valor })
        assertNull(vm.estado.value.outroValor); assertFalse(vm.estado.value.erroOutro)
        // tocar de novo em "Outro valor" da mesma meta fecha o campo
        vm.abrirOutro(frutas.id); vm.abrirOutro(frutas.id)
        assertNull(vm.estado.value.outroValor)
    }

    @Test fun falhaAoGravarERecusaDoServidorAvisam() = runTest {
        val caminhada = meta(TipoMeta.ATIVIDADE_MIN)
        val porta = Porta(DadosMetasPaciente(listOf(caminhada)))
        val vm = criar(porta)
        porta.falha = true
        vm.somar(caminhada.id, 10.0)
        assertEquals(MensagemMetasPaciente.ERRO, vm.estado.value.erro)
        vm.tentarNovamente(); assertNull(vm.estado.value.erro)
        porta.recusas.emit(Unit)
        assertEquals(MensagemMetasPaciente.ERRO, vm.estado.value.erro)
    }

    @Test fun semanaAbreEFecha() = runTest {
        val vm = criar(Porta(DadosMetasPaciente(listOf(meta(TipoMeta.ATIVIDADE_MIN, frequencia = FrequenciaMeta.SEMANAL, vezes = 3)))))
        vm.abrirSemana(); assertTrue(vm.estado.value.verSemana)
        vm.fecharSemana(); assertFalse(vm.estado.value.verSemana)
    }

    @Test fun lerValorAceitaVirgulaEPonto() {
        assertEquals(1.5, lerValorMeta("1,5")); assertEquals(1.5, lerValorMeta("1.5"))
        assertNull(lerValorMeta("0")); assertNull(lerValorMeta("")); assertNull(lerValorMeta("100001"))
    }

    // ---- MOB-90b
    @Test fun ligarLembreteUsaOsHorariosDoTipoEEditarValida() = runTest {
        val caminhada = meta(TipoMeta.ATIVIDADE_MIN)
        val porta = Porta(DadosMetasPaciente(listOf(caminhada)))
        val vm = criar(porta)
        vm.alternarLembrete(caminhada.id, true)
        assertEquals(listOf(LocalTime(20, 0)), vm.estado.value.lembretes[caminhada.id])
        vm.editarHorarios(caminhada.id)
        assertEquals("20:00", vm.estado.value.textoHorarios)
        vm.mudarTextoHorarios("7h, 25:00"); vm.salvarHorarios()
        assertTrue(vm.estado.value.erroHorarios); assertEquals(listOf(LocalTime(20, 0)), porta.lembretes.value[caminhada.id])
        vm.mudarTextoHorarios("18:30, 7h"); vm.salvarHorarios()
        assertNull(vm.estado.value.editandoHorarios)
        assertEquals(listOf(LocalTime(7, 0), LocalTime(18, 30)), vm.estado.value.lembretes[caminhada.id])
        vm.alternarLembrete(caminhada.id, false)
        assertNull(vm.estado.value.lembretes[caminhada.id])
        // meta que não está na semana não liga
        vm.alternarLembrete(Uuid.random(), true)
        assertTrue(porta.lembretes.value.isEmpty())
    }

    @Test fun falhaAoGuardarOLembreteMostraErro() = runTest {
        val caminhada = meta(TipoMeta.ATIVIDADE_MIN)
        val porta = Porta(DadosMetasPaciente(listOf(caminhada))).apply { falha = true }
        val vm = criar(porta)
        vm.alternarLembrete(caminhada.id, true)
        assertEquals(MensagemMetasPaciente.ERRO, vm.estado.value.erro)
    }
}
