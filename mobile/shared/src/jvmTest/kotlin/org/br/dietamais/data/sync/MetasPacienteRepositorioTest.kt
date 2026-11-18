package org.br.dietamais.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.br.dietamais.data.local.BancoDieta
import org.br.dietamais.data.remoto.JsonContrato
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.MetasPacienteRepositorio
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-90: a meta vem do sync; o registro do paciente nasce no banco local e na fila na mesma transação (RN-MET-02, RN-SYN-01). */
class MetasPacienteRepositorioTest {
    private val usuario = Uuid.parse("019a0f32-5000-7000-8000-000000000003")
    private val agora = Instant.parse("2026-11-17T12:00:00Z")

    private fun comBanco(bloco: suspend (BancoDieta) -> Unit) = runTest {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            BancoDieta.Schema.create(driver)
            val banco = BancoDieta(driver); banco.sessaoQueries.salvar(usuario.toString(), "PACIENTE"); bloco(banco)
        }
    }

    @Test fun leAMetaDoSyncEGravaORegistroNaFila() = comBanco { banco ->
        val meta = MetaResposta(Uuid.random(), Uuid.random(), TipoMeta.ATIVIDADE_MIN, alvo = 30.0, frequencia = FrequenciaMeta.DIARIA, versao = 1, criadoEm = agora, atualizadoEm = agora)
        banco.registroSyncQueries.salvar(usuario.toString(), "PACIENTE", "metas", meta.id.toString(), 1, 0, JsonContrato.encodeToString(meta))
        val repo = MetasPacienteRepositorio(banco)
        repo.registrar(meta.id, LocalDate(2026, 11, 17), 10.0, agora)
        val dados = repo.observar().first()
        assertEquals(meta.id, dados.metas.single().id)
        val registro = dados.registros.single()
        assertEquals(meta.id, registro.metaId); assertEquals(10.0, registro.valor); assertEquals(LocalDate(2026, 11, 17), registro.data)
        val fila = FilaMutacoes(banco).pendentes().single()
        assertEquals("registrosMeta", fila.entidade); assertEquals(OperacaoMutacao.CRIAR, fila.operacao); assertEquals(registro.id, fila.id)
        assertEquals("\"${meta.id}\"", fila.dados["metaId"].toString())
        assertEquals("\"2026-11-17\"", fila.dados["data"].toString())
    }

    @Test fun valorInvalidoNaoGravaNada() = comBanco { banco ->
        val repo = MetasPacienteRepositorio(banco)
        assertFailsWith<IllegalArgumentException> { repo.registrar(Uuid.random(), LocalDate(2026, 11, 17), 0.0, agora) }
        assertFailsWith<IllegalArgumentException> { repo.registrar(Uuid.random(), LocalDate(2026, 11, 17), 100_001.0, agora) }
        assertTrue(FilaMutacoes(banco).pendentes().isEmpty())
        assertTrue(repo.observar().first().registros.isEmpty())
    }

    // ---- MOB-90b
    @Test fun lembreteDaMetaFicaSoNoAparelhoEForaDaFila() = comBanco { banco ->
        val repo = MetasPacienteRepositorio(banco)
        val meta = Uuid.random(); val outra = Uuid.random()
        assertTrue(repo.observarLembretes().first().isEmpty())
        repo.definirLembrete(meta, listOf(LocalTime(17, 0), LocalTime(9, 0), LocalTime(9, 0)))
        repo.definirLembrete(outra, listOf(LocalTime(20, 30)))
        assertEquals(mapOf(meta to listOf(LocalTime(9, 0), LocalTime(17, 0)), outra to listOf(LocalTime(20, 30))), repo.observarLembretes().first())
        repo.definirLembrete(meta, emptyList())
        assertEquals(setOf(outra), repo.observarLembretes().first().keys)
        assertTrue(FilaMutacoes(banco).pendentes().isEmpty())
    }
}
