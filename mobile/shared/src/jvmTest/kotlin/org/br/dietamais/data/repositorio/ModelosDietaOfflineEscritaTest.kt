package org.br.dietamais.data.repositorio

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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

/** MOB-29d: modelos de dieta dela gravados no aparelho e na fila `modelosDieta`; o servidor decide no /sync (BE-83). */
class ModelosDietaOfflineEscritaTest {
    private val usuario = Uuid.parse("019a0f32-5000-7000-8000-000000000003")
    private val contexto = ContextoLeituraSync(usuario, Perfil.NUTRICIONISTA, "epoca")
    private val modeloId = Uuid.parse("019a0f32-5000-7000-8000-0000000000f1")
    private val sistemaId = Uuid.parse("019a0f32-5000-7000-8000-0000000000f9")
    private val grupo = Uuid.parse("019a0f32-5000-7000-8000-0000000000e1")
    private val momento = Instant.parse("2026-11-17T12:00:00Z")

    private class Online : PortaModelosDieta {
        var lista: List<ModeloDietaResposta>? = null
        val duplicados = mutableListOf<Uuid>()
        val aplicados = mutableListOf<Uuid>()
        override suspend fun listar() = lista ?: throw FalhaComunicacao()
        override suspend fun duplicar(id: Uuid, chave: Uuid): ModeloDietaResposta { duplicados += id; throw FalhaComunicacao() }
        override suspend fun excluir(id: Uuid, versao: Long) = error("excluir vai pela fila")
        override suspend fun criar(requisicao: SalvarModeloDietaRequisicao, chave: Uuid): ModeloDietaResposta = error("criar vai pela fila")
        override suspend fun atualizar(id: Uuid, versao: Long, requisicao: SalvarModeloDietaRequisicao): ModeloDietaResposta = error("atualizar vai pela fila")
        override suspend fun salvarComoModelo(dietaId: Uuid, requisicao: SalvarComoModeloRequisicao, chave: Uuid): ModeloDietaResposta = error("não usado")
        override suspend fun referenciaAvaliacao(pacienteId: Uuid): ReferenciaAvaliacaoDietaResposta? = null
        override suspend fun aplicar(pacienteId: Uuid, modeloId: Uuid, requisicao: AplicarModeloRequisicao, chave: Uuid): DietaDeModeloResposta {
            aplicados += modeloId; throw FalhaComunicacao()
        }
    }

    private class Fonte(var resposta: suspend (List<MutacaoPendente>) -> List<ResultadoMutacaoResposta>) : FonteEnvioSync {
        val lotes = mutableListOf<List<MutacaoPendente>>()
        override suspend fun enviar(lote: List<MutacaoPendente>): List<ResultadoMutacaoResposta> { lotes += lote; return resposta(lote) }
    }

    private fun comBanco(bloco: suspend (BancoDieta, Online, ModelosDietaOffline) -> Unit) = runTest {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            BancoDieta.Schema.create(driver)
            val banco = BancoDieta(driver); banco.sessaoQueries.salvar(usuario.toString(), "NUTRICIONISTA")
            val online = Online()
            bloco(banco, online, ModelosDietaOffline(online, banco, agora = { momento }))
        }
    }

    private fun pedido(titulo: String, id: Uuid? = null) = SalvarModeloDietaRequisicao(id = id, titulo = titulo,
        refeicoes = listOf(RefeicaoRequisicao(nome = "Café", horario = LocalTime(7, 0), ordem = 0, itens = listOf(ItemRefeicaoRequisicao(grupoId = grupo, porcoes = 2.0)))),
        porcoes = listOf(PorcaoGrupo(grupo, 1)), kcalReferencia = 1800.0)
    private fun doServidor(titulo: String, versao: Long, id: Uuid = modeloId, escopo: String = "NUTRI") = ModeloDietaResposta(id, escopo, titulo,
        refeicoes = pedido(titulo).refeicoes, porcoes = pedido(titulo).porcoes, kcalReferencia = 1800.0, versao = versao, criadoEm = momento, atualizadoEm = momento)
    private fun semear(banco: BancoDieta, m: ModeloDietaResposta) =
        banco.registroSyncQueries.salvar(usuario.toString(), "NUTRICIONISTA", "modelosDieta", m.id.toString(), m.versao, 0, JsonContrato.encodeToString(m))
    private fun local(banco: BancoDieta, id: Uuid = modeloId) = banco.registroSyncQueries.dadosDe(usuario.toString(), "NUTRICIONISTA", "modelosDieta", id.toString())
        .executeAsOneOrNull()?.dados_json?.let { JsonContrato.decodeFromString<ModeloDietaResposta>(it) }
    private fun fila(banco: BancoDieta) = banco.mutacaoPendenteQueries.doEscopo(usuario.toString(), "NUTRICIONISTA").executeAsList()

    @Test
    fun criarSemRedeGravaNoAparelhoENaFila() = comBanco { banco, _, modelos ->
        val criado = modelos.criar(pedido("Hipertensão 1800"), modeloId)
        assertEquals(modeloId, criado.id)
        assertEquals("NUTRI", criado.escopo)
        assertEquals(1, criado.versao)
        assertEquals("Hipertensão 1800", local(banco)?.titulo)
        val m = fila(banco).single()
        assertEquals("modelosDieta", m.entidade); assertEquals("CRIAR", m.operacao)
        assertEquals(modeloId.toString(), Json.parseToJsonElement(m.dados_json).jsonObject["id"]?.jsonPrimitive?.content)
        assertEquals(setOf(modeloId), modelos.pendentesDeEnvio())
        // sem rede a lista mostra o modelo criado no aparelho
        assertEquals(listOf(modeloId), modelos.listar().map { it.id })
    }

    @Test
    fun editarDuasVezesJuntaNumaMutacaoSo() = comBanco { banco, _, modelos ->
        semear(banco, doServidor("Base", 3))
        val primeira = modelos.atualizar(modeloId, 3, pedido("Base v2"))
        assertEquals(4, primeira.versao)
        modelos.atualizar(modeloId, 4, pedido("Base v3"))
        val m = fila(banco).single()
        assertEquals("ATUALIZAR", m.operacao); assertEquals(3L, m.versao_base)
        assertEquals("Base v3", Json.parseToJsonElement(m.dados_json).jsonObject["titulo"]?.jsonPrimitive?.content)
        assertNull(Json.parseToJsonElement(m.dados_json).jsonObject["id"]?.takeUnless { it is JsonNull })
        assertEquals("Base v3", local(banco)?.titulo)
    }

    @Test
    fun versaoAntigaOuConflitoAbertoBloqueiaEdicao() = comBanco { banco, _, modelos ->
        semear(banco, doServidor("Base", 3))
        assertEquals("CONFLITO_VERSAO", assertFailsWith<ErroApi> { modelos.atualizar(modeloId, 2, pedido("x")) }.codigo)
        modelos.atualizar(modeloId, 3, pedido("Minha"))
        val fonte = Fonte { l -> l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.CONFLITO, codigo = "CONFLITO_VERSAO",
            atual = JsonContrato.encodeToJsonElement(doServidor("Outro aparelho", 4))) } }
        EnviadorFila(fonte, banco, { contexto }).enviar()
        assertEquals("CONFLITO_VERSAO", assertFailsWith<ErroApi> { modelos.atualizar(modeloId, 4, pedido("Mais uma")) }.codigo)
        assertEquals("CONFLITO_VERSAO", assertFailsWith<ErroApi> { modelos.excluir(modeloId, 4) }.codigo)
    }

    @Test
    fun excluirModeloDoServidorVaiPelaFilaEVoltaSeRecusado() = comBanco { banco, online, modelos ->
        semear(banco, doServidor("Base", 3))
        modelos.excluir(modeloId, 3)
        assertNull(local(banco))
        val m = fila(banco).single()
        assertEquals("EXCLUIR", m.operacao); assertEquals(3L, m.versao_base); assertEquals("{}", m.dados_json)
        // com rede, a lista do servidor ainda traz o modelo, mas a exclusão local vale mais
        online.lista = listOf(doServidor("Base", 3))
        assertTrue(modelos.listar().none { it.id == modeloId })
        EnviadorFila(Fonte { l -> l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.REJEITADA, codigo = "ASSINATURA_INATIVA") } }, banco, { contexto }).enviar()
        assertEquals("Base", local(banco)?.titulo)
    }

    @Test
    fun excluirAplicadoLimpaFila() = comBanco { banco, _, modelos ->
        semear(banco, doServidor("Base", 3))
        modelos.excluir(modeloId, 3)
        EnviadorFila(Fonte { l -> l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.APLICADA, 4) } }, banco, { contexto }).enviar()
        assertTrue(fila(banco).isEmpty())
        assertNull(local(banco))
        assertNull(banco.registroSyncQueries.dadosDe(usuario.toString(), "NUTRICIONISTA", ANTERIOR + "modelosDieta", modeloId.toString()).executeAsOneOrNull())
    }

    @Test
    fun excluirComEdicaoNaFilaViraExclusaoNaMesmaMutacao() = comBanco { banco, _, modelos ->
        semear(banco, doServidor("Base", 3))
        modelos.atualizar(modeloId, 3, pedido("Editado"))
        modelos.excluir(modeloId, 4)
        val m = fila(banco).single()
        assertEquals("EXCLUIR", m.operacao); assertEquals(3L, m.versao_base)
        assertNull(local(banco))
        // recusado: volta ao estado de antes da primeira edição
        EnviadorFila(Fonte { l -> l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.REJEITADA, codigo = "ACESSO_NEGADO") } }, banco, { contexto }).enviar()
        assertEquals("Base", local(banco)?.titulo)
    }

    @Test
    fun excluirModeloQueNuncaSaiuDoAparelhoSoApagaLocal() = comBanco { banco, _, modelos ->
        modelos.criar(pedido("Rascunho"), modeloId)
        modelos.excluir(modeloId, 1)
        assertTrue(fila(banco).isEmpty())
        assertNull(local(banco))
    }

    @Test
    fun duplicarModeloDelaCriaCopiaLocalEDoSistemaVaiOnline() = comBanco { banco, online, modelos ->
        semear(banco, doServidor("Base", 3))
        val copia = modelos.duplicar(modeloId, Uuid.parse("019a0f32-5000-7000-8000-0000000000f2"))
        assertEquals("Base (cópia)", copia.titulo)
        assertEquals("CRIAR", fila(banco).single().operacao)
        assertTrue(online.duplicados.isEmpty())
        assertFailsWith<FalhaComunicacao> { modelos.duplicar(sistemaId) }
        assertEquals(listOf(sistemaId), online.duplicados)
    }

    @Test
    fun modeloDoSistemaNaoSeEditaNemSeApaga() = comBanco { banco, _, modelos ->
        semear(banco, doServidor("Sistema", 1, sistemaId, "SISTEMA"))
        assertEquals("ACESSO_NEGADO", assertFailsWith<ErroApi> { modelos.atualizar(sistemaId, 1, pedido("x")) }.codigo)
        assertEquals("ACESSO_NEGADO", assertFailsWith<ErroApi> { modelos.excluir(sistemaId, 1) }.codigo)
        assertTrue(fila(banco).isEmpty())
    }

    @Test
    fun aplicarModeloAindaNaFilaPedeEnvioAntes() = comBanco { _, online, modelos ->
        modelos.criar(pedido("Novo"), modeloId)
        assertFailsWith<ModeloPendente> { modelos.aplicar(Uuid.random(), modeloId, AplicarModeloRequisicao()) }
        assertTrue(online.aplicados.isEmpty())
    }

    @Test
    fun edicaoDuranteOEnvioSegueComoAtualizacaoDepoisDeAplicada() = comBanco { banco, _, modelos ->
        semear(banco, doServidor("Base", 3))
        modelos.atualizar(modeloId, 3, pedido("Primeira"))
        var editou = false
        val fonte = Fonte { l ->
            if (!editou) { editou = true; modelos.atualizar(modeloId, 4, pedido("Segunda")) }
            l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.APLICADA, 4) }
        }
        EnviadorFila(fonte, banco, { contexto }).enviar()
        assertEquals("Primeira", fonte.lotes.first().single().dados["titulo"]?.jsonPrimitive?.content)
        assertEquals(2, fonte.lotes.size)
        val segunda = fonte.lotes[1].single()
        assertEquals(OperacaoMutacao.ATUALIZAR, segunda.operacao); assertEquals(4L, segunda.versaoBase)
        assertEquals("Segunda", segunda.dados["titulo"]?.jsonPrimitive?.content)
        assertTrue(fila(banco).isEmpty())
    }

    @Test
    fun exclusaoDuranteOEnvioSegueComoExclusaoDepoisDeAplicada() = comBanco { banco, _, modelos ->
        semear(banco, doServidor("Base", 3))
        modelos.atualizar(modeloId, 3, pedido("Primeira"))
        var excluiu = false
        val fonte = Fonte { l ->
            if (!excluiu) { excluiu = true; modelos.excluir(modeloId, 4) }
            l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.APLICADA, 4) }
        }
        EnviadorFila(fonte, banco, { contexto }).enviar()
        assertEquals(2, fonte.lotes.size)
        val segunda = fonte.lotes[1].single()
        assertEquals(OperacaoMutacao.EXCLUIR, segunda.operacao); assertEquals(4L, segunda.versaoBase)
        assertTrue(fila(banco).isEmpty())
        assertNull(local(banco))
    }
}
