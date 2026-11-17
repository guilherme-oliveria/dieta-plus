package org.br.dietamais.data.repositorio

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.br.dietamais.data.local.BancoDieta
import org.br.dietamais.data.remoto.ErroApi
import org.br.dietamais.data.remoto.JsonContrato
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.sync.*
import org.br.dietamais.ui.nutri.anamnese.PortaAnamneseFalsa
import org.br.dietamais.ui.nutri.anamnese.perguntaFalsa
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** MOB-29c: modelos de questionário criados e editados no aparelho, com a mutação `modelos` na fila; enviar ao paciente segue online. */
class ModelosQuestionarioOfflineTest {
    private val usuario = Uuid.parse("019a0f32-5000-7000-8000-000000000003")
    private val contexto = ContextoLeituraSync(usuario, Perfil.NUTRICIONISTA, "epoca")
    private val modeloId = Uuid.parse("019a0f32-5000-7000-8000-0000000000c1")
    private val paciente = Uuid.parse("019a0f32-5000-7000-8000-0000000000aa")
    private val momento = Instant.parse("2026-11-17T12:00:00Z")

    private class Fonte(var resposta: suspend (List<MutacaoPendente>) -> List<ResultadoMutacaoResposta>) : FonteEnvioSync {
        override suspend fun enviar(lote: List<MutacaoPendente>): List<ResultadoMutacaoResposta> = resposta(lote)
    }

    private fun comBanco(bloco: suspend (BancoDieta, PortaAnamneseFalsa, ModelosQuestionarioOffline) -> Unit) = runTest {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            BancoDieta.Schema.create(driver)
            val banco = BancoDieta(driver); banco.sessaoQueries.salvar(usuario.toString(), "NUTRICIONISTA")
            val online = PortaAnamneseFalsa()
            bloco(banco, online, ModelosQuestionarioOffline(online, banco, agora = { momento }))
        }
    }

    private fun pedido(nome: String, id: Uuid? = null) = SalvarModeloRequisicao(id, nome, listOf(BlocoQuestionario("Hábitos", listOf(perguntaFalsa("Bebe água?")))))
    private fun doServidor(nome: String, versao: Long) = ModeloQuestionarioResposta(modeloId, nome, false, pedido(nome).blocos, versao, momento, momento)
    private fun semear(banco: BancoDieta, m: ModeloQuestionarioResposta) =
        banco.registroSyncQueries.salvar(usuario.toString(), "NUTRICIONISTA", "modelos", m.id.toString(), m.versao, 0, JsonContrato.encodeToString(m))
    private fun local(banco: BancoDieta) = banco.registroSyncQueries.dadosDe(usuario.toString(), "NUTRICIONISTA", "modelos", modeloId.toString())
        .executeAsOneOrNull()?.dados_json?.let { JsonContrato.decodeFromString<ModeloQuestionarioResposta>(it) }
    private fun fila(banco: BancoDieta) = banco.mutacaoPendenteQueries.doEscopo(usuario.toString(), "NUTRICIONISTA").executeAsList()
    private fun rejeitar() = Fonte { l -> l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.REJEITADA, codigo = "ASSINATURA_INATIVA") } }
    private fun nome(json: String) = Json.parseToJsonElement(json).jsonObject["nome"]?.jsonPrimitive?.content

    @Test
    fun criarSemRedeGravaNoAparelhoENaFila() = comBanco { banco, online, modelos ->
        val criado = modelos.criarModelo(pedido("Anamnese esportiva", modeloId))
        assertEquals(modeloId, criado.id); assertEquals(1, criado.versao); assertFalse(criado.padrao)
        assertEquals("Anamnese esportiva", local(banco)?.nome)
        val m = fila(banco).single()
        assertEquals("modelos", m.entidade); assertEquals("CRIAR", m.operacao); assertEquals(modeloId.toString(), m.id)
        assertEquals(setOf(modeloId), modelos.modelosPendentes())
        assertNull(online.modeloCriado)
    }

    @Test
    fun editarDuasVezesJuntaNumaMutacaoSo() = comBanco { banco, _, modelos ->
        semear(banco, doServidor("Base", 3))
        assertEquals(4, modelos.salvarModelo(modeloId, 3, pedido("Base v2")).versao)
        assertEquals(4, modelos.salvarModelo(modeloId, 4, pedido("Base v3")).versao)
        val m = fila(banco).single()
        assertEquals("ATUALIZAR", m.operacao); assertEquals(3L, m.versao_base); assertEquals("Base v3", nome(m.dados_json))
        assertEquals("Base v3", local(banco)?.nome)
    }

    @Test
    fun editarModeloQueAindaNaoSaiuContinuaCriacao() = comBanco { banco, _, modelos ->
        modelos.criarModelo(pedido("Rascunho", modeloId))
        modelos.salvarModelo(modeloId, 1, pedido("Pronto"))
        val m = fila(banco).single()
        assertEquals("CRIAR", m.operacao); assertEquals("Pronto", nome(m.dados_json))
        assertEquals(modeloId.toString(), Json.parseToJsonElement(m.dados_json).jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun versaoAntigaOuConflitoAbertoBloqueia() = comBanco { banco, _, modelos ->
        semear(banco, doServidor("Base", 3))
        assertEquals("CONFLITO_VERSAO", assertFailsWith<ErroApi> { modelos.salvarModelo(modeloId, 2, pedido("x")) }.codigo)
        assertEquals("RECURSO_NAO_ENCONTRADO", assertFailsWith<ErroApi> { modelos.salvarModelo(Uuid.random(), 1, pedido("x")) }.codigo)
        modelos.salvarModelo(modeloId, 3, pedido("Minha"))
        EnviadorFila(Fonte { l -> l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.CONFLITO, codigo = "CONFLITO_VERSAO",
            atual = JsonContrato.encodeToJsonElement(doServidor("Outro aparelho", 4))) } }, banco, { contexto }).enviar()
        assertEquals(setOf(modeloId), modelos.modelosPendentes())
        assertEquals("CONFLITO_VERSAO", assertFailsWith<ErroApi> { modelos.salvarModelo(modeloId, 4, pedido("Mais uma")) }.codigo)
    }

    @Test
    fun enviarAoPacienteEsperaOModeloChegarAoServidor() = comBanco { banco, online, modelos ->
        modelos.criarModelo(pedido("Novo", modeloId))
        assertFailsWith<ModeloQuestionarioPendente> { modelos.enviar(paciente, EnviarQuestionarioRequisicao(Uuid.random(), modeloId)) }
        assertNull(online.enviado)
        EnviadorFila(Fonte { l -> l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.APLICADA, versao = 1) } }, banco, { contexto }).enviar()
        assertTrue(modelos.modelosPendentes().isEmpty())
        online.modelos.value = listOf(doServidor("Novo", 1))
        modelos.enviar(paciente, EnviarQuestionarioRequisicao(Uuid.random(), modeloId))
        assertEquals(modeloId, online.enviado?.modeloId)
    }

    @Test
    fun rejeicaoDeCriacaoTiraOModeloDoAparelho() = comBanco { banco, _, modelos ->
        modelos.criarModelo(pedido("Novo", modeloId))
        EnviadorFila(rejeitar(), banco, { contexto }).enviar()
        assertNull(local(banco))
        assertTrue(modelos.modelosPendentes().isEmpty())
    }

    @Test
    fun rejeicaoDeEdicaoVoltaAoAnterior() = comBanco { banco, _, modelos ->
        val rejeitar = rejeitar()
        semear(banco, doServidor("Base", 3))
        modelos.salvarModelo(modeloId, 3, pedido("Editado"))
        EnviadorFila(rejeitar, banco, { contexto }).enviar()
        assertEquals("Base", local(banco)?.nome)
    }
}
