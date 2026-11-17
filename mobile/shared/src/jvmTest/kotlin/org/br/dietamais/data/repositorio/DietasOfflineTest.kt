package org.br.dietamais.data.repositorio

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.*
import org.br.dietamais.data.local.BancoDieta
import org.br.dietamais.data.remoto.ErroApi
import org.br.dietamais.data.remoto.FalhaComunicacao
import org.br.dietamais.data.remoto.JsonContrato
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.sync.*
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-29b: rascunho de dieta gravado no aparelho e na fila na mesma transação; o servidor decide no /sync. */
class DietasOfflineTest {
    private val usuario = Uuid.parse("019a0f32-5000-7000-8000-000000000003")
    private val contexto = ContextoLeituraSync(usuario, Perfil.NUTRICIONISTA, "epoca")
    private val paciente = Uuid.parse("019a0f32-5000-7000-8000-0000000000b1")
    private val dietaId = Uuid.parse("019a0f32-5000-7000-8000-0000000000d1")
    private val grupo = Uuid.parse("019a0f32-5000-7000-8000-0000000000e1")
    private val momento = Instant.parse("2026-11-17T12:00:00Z")

    private class Online : PortaDietas {
        var lista: List<DietaResposta>? = null
        val publicadas = mutableListOf<Uuid>()
        override suspend fun listar(pacienteId: Uuid) = lista ?: throw FalhaComunicacao()
        override suspend fun criar(pacienteId: Uuid, requisicao: SalvarDietaRequisicao, chave: Uuid): DietaResposta = error("criar vai pela fila")
        override suspend fun salvar(id: Uuid, versao: Long, requisicao: SalvarDietaRequisicao): DietaResposta = error("salvar vai pela fila")
        override suspend fun publicar(id: Uuid, versao: Long): DietaSnapshotResposta { publicadas += id; throw FalhaComunicacao() }
        override suspend fun duplicar(id: Uuid, chave: Uuid): DietaResposta = error("não usado")
    }

    private class Fonte(var resposta: suspend (List<MutacaoPendente>) -> List<ResultadoMutacaoResposta>) : FonteEnvioSync {
        val lotes = mutableListOf<List<MutacaoPendente>>()
        override suspend fun enviar(lote: List<MutacaoPendente>): List<ResultadoMutacaoResposta> { lotes += lote; return resposta(lote) }
    }

    private fun comBanco(bloco: suspend (BancoDieta, Online, DietasOffline) -> Unit) = runTest {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            BancoDieta.Schema.create(driver)
            val banco = BancoDieta(driver); banco.sessaoQueries.salvar(usuario.toString(), "NUTRICIONISTA")
            val online = Online()
            bloco(banco, online, DietasOffline(online, banco, agora = { momento }))
        }
    }

    private fun pedido(titulo: String, id: Uuid? = null) = SalvarDietaRequisicao(id = id, titulo = titulo,
        refeicoes = listOf(RefeicaoRequisicao(nome = "Café", horario = LocalTime(7, 0), ordem = 0, itens = listOf(ItemRefeicaoRequisicao(grupoId = grupo, porcoes = 2.0)))),
        porcoes = listOf(PorcaoGrupo(grupo, 1)))
    private fun doServidor(titulo: String, versao: Long) = DietaResposta(dietaId, paciente, Uuid.random(), titulo, StatusDieta.RASCUNHO, null, emptyList(),
        TotaisNutricionaisResposta(1800.0, 200.0, 90.0, 60.0), versao, momento, momento)
    private fun semear(banco: BancoDieta, dieta: DietaResposta) =
        banco.registroSyncQueries.salvar(usuario.toString(), "NUTRICIONISTA", "dietas", dieta.id.toString(), dieta.versao, 0, JsonContrato.encodeToString(dieta))
    private fun local(banco: BancoDieta, id: Uuid = dietaId) = banco.registroSyncQueries.dadosDe(usuario.toString(), "NUTRICIONISTA", "dietas", id.toString())
        .executeAsOneOrNull()?.dados_json?.let { JsonContrato.decodeFromString<DietaResposta>(it) }
    private fun fila(banco: BancoDieta) = banco.mutacaoPendenteQueries.doEscopo(usuario.toString(), "NUTRICIONISTA").executeAsList()
    private fun titulo(dados: String) = Json.parseToJsonElement(dados).jsonObject["titulo"]?.jsonPrimitive?.content

    @Test fun criarSemRedeGravaRascunhoEFilaComPacienteIdEPrevia() = comBanco { banco, _, repo ->
        val previa = PreviaDieta(TotaisNutricionaisResposta(1500.0, 180.0, 80.0, 50.0))
        val criada = repo.criar(paciente, pedido("Plano A", dietaId), Uuid.random(), previa)
        assertEquals(1L, criada.versao); assertEquals(StatusDieta.RASCUNHO, criada.status); assertEquals(1500.0, criada.totais.kcal)
        assertEquals(1, criada.refeicoes.single().itens.single().opcao)
        val m = fila(banco).single()
        assertEquals("CRIAR", m.operacao); assertEquals(dietaId.toString(), m.id)
        val dados = Json.parseToJsonElement(m.dados_json).jsonObject
        assertEquals(paciente.toString(), dados["pacienteId"]?.jsonPrimitive?.content)
        assertEquals("Plano A", local(banco)?.titulo)
        // sem rede, a lista vem do aparelho
        assertEquals(listOf(dietaId), repo.listar(paciente).map { it.id })
        assertEquals(setOf(dietaId), repo.pendentesDeEnvio()); assertEquals(setOf(dietaId), repo.rascunhosSoLocais())
        assertTrue(repo.escritaOffline)
    }

    @Test fun listarComRedeGuardaDoServidorMasNaoPassaPorCimaDaEdicaoLocal() = comBanco { banco, online, repo ->
        semear(banco, doServidor("Servidor", 3))
        repo.salvar(dietaId, 3, pedido("Minha"))
        val outra = doServidor("Outra", 2).copy(id = Uuid.parse("019a0f32-5000-7000-8000-0000000000d2"))
        online.lista = listOf(doServidor("Servidor", 3), outra)
        val lista = repo.listar(paciente)
        assertEquals(setOf("Minha", "Outra"), lista.map { it.titulo }.toSet())
        assertEquals("Outra", local(banco, outra.id)?.titulo)
        assertEquals("Minha", local(banco)?.titulo)
    }

    @Test fun duasEdicoesConcorrentesDoMesmoRascunhoViramUmaMutacaoEOConflitoBloqueiaAOutra() = comBanco { banco, _, repo ->
        semear(banco, doServidor("Servidor", 3))
        val primeira = repo.salvar(dietaId, 3, pedido("Edição 1"))
        assertEquals(4L, primeira.versao)
        val segunda = repo.salvar(dietaId, primeira.versao, pedido("Edição 2"))
        // uma mutação só, com a base do servidor e o conteúdo mais novo
        val m = fila(banco).single()
        assertEquals(3L, m.versao_base); assertEquals("Edição 2", titulo(m.dados_json)); assertEquals(4L, segunda.versao)
        assertEquals("Edição 2", local(banco)?.titulo)
        // outro aparelho editou no servidor: conflito; a próxima edição local espera a decisão
        val servidor = JsonContrato.encodeToJsonElement(doServidor("Web", 4))
        val fonte = Fonte { l -> l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.CONFLITO, codigo = "CONFLITO_VERSAO", atual = servidor) } }
        EnviadorFila(fonte, banco, { contexto }).enviar()
        assertEquals("CONFLITO", fila(banco).single().status)
        assertEquals("CONFLITO_VERSAO", assertFailsWith<ErroApi> { repo.salvar(dietaId, 4, pedido("Edição 3")) }.codigo)
        assertEquals(1, fila(banco).size)
        // manter a minha reenvia com a versão do servidor
        val pendencias = PendenciasRepositorio(banco)
        pendencias.manterMinha(pendencias.observar().first().single().idMutacao)
        val reaberta = fila(banco).single()
        assertEquals("PENDENTE", reaberta.status); assertEquals(4L, reaberta.versao_base); assertEquals("Edição 2", titulo(reaberta.dados_json))
    }

    @Test fun edicaoDuranteOEnvioSegueComoAtualizacaoSobreAVersaoAceita() = comBanco { banco, _, repo ->
        repo.criar(paciente, pedido("Antes", dietaId), Uuid.random())
        var editou = false
        val fonte = Fonte { l ->
            // a nutri salva de novo enquanto o primeiro lote está no ar
            if (!editou) { editou = true; repo.salvar(dietaId, 1, pedido("Durante")) }
            l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.APLICADA, 1) }
        }
        EnviadorFila(fonte, banco, { contexto }).enviar()
        assertEquals("Antes", titulo(fonte.lotes.first().single().dados.toString()))
        assertEquals(2, fonte.lotes.size)
        assertEquals(OperacaoMutacao.ATUALIZAR, fonte.lotes[1].single().operacao)
        assertEquals(1L, fonte.lotes[1].single().versaoBase); assertEquals("Durante", titulo(fonte.lotes[1].single().dados.toString()))
        assertTrue(fila(banco).isEmpty())
        assertEquals("Durante", local(banco)?.titulo)
    }

    @Test fun rejeicaoDaCriacaoSomeComORascunhoEPublicarEsperaOEnvio() = comBanco { banco, online, repo ->
        repo.criar(paciente, pedido("Recusada", dietaId), Uuid.random())
        assertFailsWith<RascunhoPendente> { repo.publicar(dietaId, 1) }
        assertTrue(online.publicadas.isEmpty())
        EnviadorFila(Fonte { l -> l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.REJEITADA, codigo = "ASSINATURA_INATIVA") } }, banco, { contexto }).enviar()
        assertNull(local(banco)); assertTrue(repo.pendentesDeEnvio().isEmpty())
        assertEquals("ASSINATURA_INATIVA", PendenciasRepositorio(banco).observar().first().single().codigo)
    }

    @Test fun descartarSoApagaRascunhoQueNuncaSaiuDoAparelho() = comBanco { banco, _, repo ->
        repo.criar(paciente, pedido("Só aqui", dietaId), Uuid.random())
        assertTrue(repo.descartarRascunhoLocal(dietaId))
        assertNull(local(banco)); assertTrue(fila(banco).isEmpty())
        // a que já está no servidor não se apaga (o backend não tem exclusão de dieta)
        semear(banco, doServidor("Servidor", 3))
        repo.salvar(dietaId, 3, pedido("Editada"))
        assertFalse(repo.descartarRascunhoLocal(dietaId))
        assertEquals(1, fila(banco).size); assertTrue(repo.rascunhosSoLocais().isEmpty())
    }

    @Test fun salvarComVersaoVelhaOuDietaPublicadaRecusaNaHora() = comBanco { banco, _, repo ->
        semear(banco, doServidor("Servidor", 3))
        assertEquals("CONFLITO_VERSAO", assertFailsWith<ErroApi> { repo.salvar(dietaId, 2, pedido("Velha")) }.codigo)
        semear(banco, doServidor("Publicada", 5).copy(status = StatusDieta.PUBLICADA))
        assertEquals("DIETA_NAO_EDITAVEL", assertFailsWith<ErroApi> { repo.salvar(dietaId, 5, pedido("Nova")) }.codigo)
        assertTrue(fila(banco).isEmpty())
    }
}
