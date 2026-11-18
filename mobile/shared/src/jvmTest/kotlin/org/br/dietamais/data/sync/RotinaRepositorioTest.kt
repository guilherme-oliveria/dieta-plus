package org.br.dietamais.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.JsonContrato
import org.br.dietamais.data.local.BancoDieta
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.RotinaRepositorio
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RotinaRepositorioTest {
    private val usuario = Uuid.parse("019a0f32-5000-7000-8000-000000000003")
    private val agora = Instant.parse("2026-10-28T09:00:00Z")

    private fun comBanco(bloco: suspend (BancoDieta) -> Unit) = runTest {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            BancoDieta.Schema.create(driver)
            val banco = BancoDieta(driver); banco.sessaoQueries.salvar(usuario.toString(), "PACIENTE"); bloco(banco)
        }
    }

    @Test fun registroLocalEMutacaoNascemJuntosComOMesmoId() = comBanco { banco ->
        val repo = RotinaRepositorio(banco)
        val dieta = Uuid.random(); val refeicao = Uuid.random(); val item = Uuid.random(); val alimento = Uuid.random()
        repo.registrarCheckin(dieta, refeicao, LocalDate(2026, 10, 28), StatusCheckin.TROCADO, listOf(TrocaCheckin(item, alimento)), agora)
        repo.registrarAgua(250, agora); repo.registrarPeso(72.5, agora)
        val dados = repo.observar().first()
        assertEquals(StatusCheckin.TROCADO, dados.checkins.single().status); assertEquals(250, dados.agua.single().ml); assertEquals(72.5, dados.pesos.single().pesoKg)
        val fila = FilaMutacoes(banco).pendentes()
        assertEquals(listOf("checkins", "agua", "pesos"), fila.map { it.entidade })
        assertTrue(fila.all { it.operacao == OperacaoMutacao.CRIAR })
        assertEquals(dados.checkins.single().id, fila[0].id); assertEquals(fila[0].id.toString(), fila[0].dados["id"]?.toString()?.trim('"'))
    }

    @Test fun valoresInvalidosNaoGravamNada() = comBanco { banco ->
        val repo = RotinaRepositorio(banco)
        assertFailsWith<IllegalArgumentException> { repo.registrarAgua(0, agora) }
        assertFailsWith<IllegalArgumentException> { repo.registrarPeso(10.0, agora) }
        assertFailsWith<IllegalArgumentException> { repo.registrarCheckin(Uuid.random(), Uuid.random(), LocalDate(2026, 10, 28), StatusCheckin.FEITO, listOf(TrocaCheckin(Uuid.random(), Uuid.random())), agora) }
        assertTrue(FilaMutacoes(banco).pendentes().isEmpty())
    }

    @Test fun humorEForaDoPlanoEntramNaFilaComOContrato() = comBanco { banco ->
        val repo = RotinaRepositorio(banco)
        val refeicao = Uuid.random()
        repo.registrarCheckin(Uuid.random(), refeicao, LocalDate(2026, 10, 28), StatusCheckin.FEITO, emptyList(), agora, 4, null)
        repo.registrarForaDoPlano(LocalDate(2026, 10, 28), "  Pão de queijo ", refeicao, agora)
        val fila = FilaMutacoes(banco).pendentes()
        assertEquals(listOf("checkins", "registrosForaDoPlano"), fila.map { it.entidade })
        assertEquals("4", fila[0].dados["fomeAntes"].toString())
        assertEquals("\"Pão de queijo\"", fila[1].dados["texto"].toString())
        assertEquals("\"$refeicao\"", fila[1].dados["refeicaoId"].toString())
        assertEquals(4, repo.observar().first().checkins.single().fomeAntes)
        assertFailsWith<IllegalArgumentException> { repo.registrarForaDoPlano(LocalDate(2026, 10, 28), "   ", null, agora) }
        assertFailsWith<IllegalArgumentException> { repo.registrarCheckin(Uuid.random(), refeicao, LocalDate(2026, 10, 28), StatusCheckin.FEITO, emptyList(), agora, 6, null) }
        assertEquals(2, FilaMutacoes(banco).pendentes().size)
    }

    // ---- MOB-99: água como meta
    private fun salvarMetaAgua(banco: BancoDieta): MetaResposta {
        val criada = Instant.parse("2026-10-01T00:00:00Z")
        val meta = MetaResposta(Uuid.random(), usuario, TipoMeta.AGUA, null, 2450.0, null, FrequenciaMeta.DIARIA, alvoPadrao = true, versao = 1, criadoEm = criada, atualizadoEm = criada)
        banco.registroSyncQueries.salvar(usuario.toString(), "PACIENTE", "metas", meta.id.toString(), 1, 0, JsonContrato.encodeToString(meta))
        return meta
    }

    @Test fun comAMetaDeAguaOCopoVaiParaRegistrosMetaNoDiaDoFuso() = comBanco { banco ->
        val repo = RotinaRepositorio(banco, fuso = TimeZone.of("America/Sao_Paulo"))
        // copo antigo, gravado antes da meta chegar ao aparelho: segue pelo caminho antigo
        repo.registrarAgua(150, agora)
        val meta = salvarMetaAgua(banco)
        // 01h UTC do dia 29 ainda é dia 28 em São Paulo
        repo.registrarAgua(300, Instant.parse("2026-10-29T01:00:00Z"))
        val fila = FilaMutacoes(banco).pendentes()
        assertEquals(listOf("agua", "registrosMeta"), fila.map { it.entidade })
        assertEquals("\"${meta.id}\"", fila[1].dados["metaId"].toString())
        assertEquals("\"2026-10-28\"", fila[1].dados["data"].toString())
        assertEquals("300.0", fila[1].dados["valor"].toString())
        val dados = repo.observar().first()
        assertEquals(listOf(meta.id), dados.metas.map { it.id })
        assertEquals(listOf(300.0), dados.registrosMeta.map { it.valor })
        assertEquals(setOf(fila[0].id), dados.aguaNaFila)
        // enviado o copo antigo, ele sai da fila (o servidor o devolve como registro da meta)
        banco.mutacaoPendenteQueries.concluir(fila[0].idMutacao.toString(), usuario.toString(), "PACIENTE")
        assertTrue(repo.observar().first().aguaNaFila.isEmpty())
    }

    @Test fun metasDeOutroTipoNaoMudamOCaminhoDaAgua() = comBanco { banco ->
        val repo = RotinaRepositorio(banco)
        val criada = Instant.parse("2026-10-01T00:00:00Z")
        val passos = MetaResposta(Uuid.random(), usuario, TipoMeta.PASSOS, null, 8000.0, null, FrequenciaMeta.DIARIA, versao = 1, criadoEm = criada, atualizadoEm = criada)
        banco.registroSyncQueries.salvar(usuario.toString(), "PACIENTE", "metas", passos.id.toString(), 1, 0, JsonContrato.encodeToString(passos))
        repo.registrarAgua(200, agora)
        assertEquals(listOf("agua"), FilaMutacoes(banco).pendentes().map { it.entidade })
        assertTrue(repo.observar().first().metas.isEmpty())
    }
}
