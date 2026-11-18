package org.br.dietamais.data.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.*
import org.br.dietamais.dominio.metas.planejarLembretesMeta
import org.br.dietamais.dominio.rotina.Lembrete
import org.br.dietamais.dominio.rotina.RotulosAcoes
import org.br.dietamais.dominio.rotina.TextosLembrete
import org.br.dietamais.plataforma.NotificacaoLocal
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-90b: o agendador junta os lembretes das metas com o texto genérico, sem dieta e sem suplemento. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgendadorLembretesMetaTest {
    // terça, 17/11/2026, 10h UTC
    private val relogio = object : Clock { override fun now() = Instant.parse("2026-11-17T10:00:00Z") }
    private val criada = Instant.parse("2026-11-01T00:00:00Z")
    private val meta = MetaResposta(Uuid.random(), Uuid.random(), TipoMeta.ATIVIDADE_MIN, null, 30.0, null, FrequenciaMeta.DIARIA, versao = 1,
        criadoEm = criada, atualizadoEm = criada, fim = LocalDate(2026, 11, 18))

    private object SemDieta : PortaDietaPaciente {
        override fun observar() = flowOf(DadosDietaPaciente())
        override suspend fun selecionar(dietaId: Uuid, versao: Long) = throw UnsupportedOperationException()
    }
    private object SemRotina : PortaRotina {
        override fun observar() = flowOf(DadosRotina())
        override fun registrarCheckin(dietaId: Uuid, refeicaoId: Uuid, data: LocalDate, status: StatusCheckin, trocas: List<TrocaCheckin>, agora: Instant) {}
        override fun registrarAgua(ml: Int, agora: Instant) {}
        override fun registrarPeso(pesoKg: Double, agora: Instant) {}
    }
    private object Preferencias : PortaPreferencias {
        override fun observar() = flowOf(null)
        override fun observarLembretes() = flowOf(true)
        override fun definirLembretes(ativo: Boolean) {}
        override suspend fun atualizar() = throw UnsupportedOperationException()
        override suspend fun salvar(requisicao: AtualizarPreferenciasRequisicao) = throw UnsupportedOperationException()
    }
    private class Notificacao : NotificacaoLocal {
        var agendados: List<Lembrete>? = null; var cancelou = 0; var consultas = 0
        override suspend fun permitido(): Boolean { consultas++; return true }
        override suspend fun pedirPermissao() = true
        override suspend fun agendar(lembretes: List<Lembrete>, textos: TextosLembrete) { agendados = lembretes }
        override suspend fun cancelarTudo() { cancelou++; agendados = null }
    }
    private class Metas(horarios: Map<Uuid, List<LocalTime>>, val dados: DadosMetasPaciente) : PortaMetasPaciente {
        val lembretes = MutableStateFlow(horarios)
        var coletas = 0
        override fun observar() = flowOf(dados).onStart { coletas++ }
        override fun registrar(metaId: Uuid, data: LocalDate, valor: Double, agora: Instant) {}
        override fun observarLembretes() = lembretes
    }

    private var textosPedidos = 0
    private fun textos(): TextosLembrete { textosPedidos++; return TextosLembrete("Dieta+", { it }, RotulosAcoes("Comi", "Troquei", "Pulei"), tituloMeta = "Dieta+", corpoMeta = "Hora da sua meta") }

    @Test fun agendaAMetaComOTextoGenericoECancelaAoDesligar() = runTest {
        val notificacao = Notificacao()
        val metas = Metas(mapOf(meta.id to listOf(LocalTime(20, 0))), DadosMetasPaciente(listOf(meta)))
        val agendador = AgendadorLembretes(SemDieta, SemRotina, Preferencias, notificacao, { textos() }, relogio, { TimeZone.UTC }, flowOf(Unit), metas = metas)
        var erro: Throwable? = null; var terminou = false
        backgroundScope.launch { try { agendador.executar(); terminou = true } catch (t: Throwable) { erro = t; throw t } }
        advanceUntilIdle()
        assertNull(erro, "o agendador falhou: ${erro?.stackTraceToString()?.take(1500)}")
        // a meta acaba amanhã: hoje e amanhã às 20h, nada depois
        assertEquals(2, planejarLembretesMeta(listOf(meta), emptyList(), metas.lembretes.value, relogio.now(), TimeZone.UTC).size, "planejador")
        assertTrue(notificacao.consultas > 0, "o agendador não chegou a conferir a permissão (terminou=$terminou, cancelou=${notificacao.cancelou}, coletas das metas=${metas.coletas})")
        assertTrue(textosPedidos > 0, "o agendador parou antes de montar os textos (cancelou ${notificacao.cancelou} vez(es))")
        val l = assertNotNull(notificacao.agendados, "nada agendado (cancelou ${notificacao.cancelou} vez(es))")
        assertEquals(listOf(Instant.parse("2026-11-17T20:00:00Z"), Instant.parse("2026-11-18T20:00:00Z")), l.map { it.quando })
        assertTrue(l.all { it.titulo == "Dieta+" && it.corpo == "Hora da sua meta" && it.dietaId == null })
        metas.lembretes.value = emptyMap(); advanceUntilIdle()
        assertNull(notificacao.agendados); assertTrue(notificacao.cancelou > 0)
    }
}
