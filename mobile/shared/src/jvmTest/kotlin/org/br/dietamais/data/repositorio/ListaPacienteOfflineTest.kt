package org.br.dietamais.data.repositorio

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
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

/** MOB-29c: a lista de substituição do paciente salva no aparelho e na fila `listas` (só ATUALIZAR, id = ficha); o servidor decide no /sync. */
class ListaPacienteOfflineTest {
    private val usuario = Uuid.parse("019a0f32-5000-7000-8000-000000000003")
    private val contexto = ContextoLeituraSync(usuario, Perfil.NUTRICIONISTA, "epoca")
    private val paciente = Uuid.parse("019a0f32-5000-7000-8000-0000000000aa")
    private val vinculo = Uuid.parse("019a0f32-5000-7000-8000-0000000000bb")
    private val grupo = Uuid.parse("019a0f32-5000-7000-8000-0000000000e1")
    private val momento = Instant.parse("2026-11-17T12:00:00Z")
    private fun alimento(n: Int) = AlimentoResposta(Uuid.parse("019a0f32-5000-7000-8000-0000000001${n.toString().padStart(2, '0')}"), null, grupo,
        "Alimento $n", "1 unidade", 50.0, true, 1, momento)
    private val a1 = alimento(1); private val a2 = alimento(2); private val a3 = alimento(3)

    private class Online : PortaListaPaciente {
        var servidor: ListaSubstituicaoResposta? = null
        val copias = mutableListOf<Uuid>()
        override suspend fun obter(pacienteId: Uuid) = servidor ?: throw FalhaComunicacao()
        override suspend fun salvar(pacienteId: Uuid, versao: Long, requisicao: AtualizarListaSubstituicaoRequisicao): ListaSubstituicaoResposta = error("salvar vai pela fila")
        override suspend fun copiarDe(pacienteId: Uuid, origemId: Uuid, versao: Long): ListaSubstituicaoResposta { copias += origemId; return checkNotNull(servidor) }
    }

    private class Fonte(var resposta: suspend (List<MutacaoPendente>) -> List<ResultadoMutacaoResposta>) : FonteEnvioSync {
        val lotes = mutableListOf<List<MutacaoPendente>>()
        override suspend fun enviar(lote: List<MutacaoPendente>): List<ResultadoMutacaoResposta> { lotes += lote; return resposta(lote) }
    }

    private fun comBanco(bloco: suspend (BancoDieta, Online, ListaPacienteOffline) -> Unit) = runTest {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            BancoDieta.Schema.create(driver)
            val banco = BancoDieta(driver); banco.sessaoQueries.salvar(usuario.toString(), "NUTRICIONISTA")
            // catálogo do aparelho: de onde vêm os dados do alimento acrescentado
            listOf(a1, a2, a3).forEach { banco.alimentoCacheQueries.salvar(usuario.toString(), "NUTRICIONISTA", "alimentos", it.id.toString(), JsonContrato.encodeToString(it)) }
            val online = Online()
            bloco(banco, online, ListaPacienteOffline(online, banco))
        }
    }

    private fun lista(versao: Long, alimentos: List<AlimentoResposta>, adicionados: List<Uuid> = emptyList(), removidos: List<Uuid> = emptyList()) =
        ListaSubstituicaoResposta(paciente, versao, alimentos, adicionados, removidos)
    /** O pull do /sync guarda a lista pelo id do vínculo. */
    private fun semearDoPull(banco: BancoDieta, l: ListaSubstituicaoResposta) =
        banco.registroSyncQueries.salvar(usuario.toString(), "NUTRICIONISTA", "listas", vinculo.toString(), l.versao, 0, JsonContrato.encodeToString(l))
    private fun fila(banco: BancoDieta) = banco.mutacaoPendenteQueries.doEscopo(usuario.toString(), "NUTRICIONISTA").executeAsList()
    private fun pedido(adicionados: List<Uuid>, removidos: List<Uuid>) = AtualizarListaSubstituicaoRequisicao(adicionados, removidos)
    private fun ids(json: String, campo: String) = Json.parseToJsonElement(json).jsonObject[campo]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun salvarSemRedeGravaNoAparelhoENaFila() = comBanco { banco, _, listas ->
        semearDoPull(banco, lista(2, listOf(a1, a2)))
        assertEquals(listOf(a1.id, a2.id), listas.obter(paciente).alimentos.map { it.id })
        val salva = listas.salvar(paciente, 2, pedido(listOf(a3.id), listOf(a1.id)))
        assertEquals(3, salva.versao)
        assertEquals(listOf(a2.id, a3.id), salva.alimentos.map { it.id })
        val m = fila(banco).single()
        assertEquals("listas", m.entidade); assertEquals("ATUALIZAR", m.operacao); assertEquals(paciente.toString(), m.id); assertEquals(2L, m.versao_base)
        assertEquals(listOf(a3.id.toString()), ids(m.dados_json, "adicionados")); assertEquals(listOf(a1.id.toString()), ids(m.dados_json, "removidos"))
        assertTrue(listas.pendenteDeEnvio(paciente))
        // sem rede, abrir de novo mostra a lista editada
        assertEquals(listOf(a2.id, a3.id), listas.obter(paciente).alimentos.map { it.id })
    }

    @Test
    fun editarDuasVezesJuntaNumaMutacaoSo() = comBanco { banco, _, listas ->
        semearDoPull(banco, lista(2, listOf(a1, a2)))
        listas.salvar(paciente, 2, pedido(listOf(a3.id), emptyList()))
        val segunda = listas.salvar(paciente, 3, pedido(listOf(a3.id), listOf(a2.id)))
        assertEquals(3, segunda.versao)
        assertEquals(listOf(a1.id, a3.id), segunda.alimentos.map { it.id })
        val m = fila(banco).single()
        assertEquals(2L, m.versao_base); assertEquals(listOf(a2.id.toString()), ids(m.dados_json, "removidos"))
    }

    @Test
    fun voltarUmAlimentoRemovidoAntesUsaOCatalogo() = comBanco { banco, _, listas ->
        // a3 estava fora da lista (removido da base); o novo pedido não o remove mais, então ele volta
        semearDoPull(banco, lista(5, listOf(a1), removidos = listOf(a3.id)))
        val salva = listas.salvar(paciente, 5, pedido(emptyList(), emptyList()))
        assertEquals(listOf(a1.id, a3.id), salva.alimentos.map { it.id })
    }

    @Test
    fun comRedeOAjustePendenteValeMaisQueOServidorECopiarEspera() = comBanco { banco, online, listas ->
        semearDoPull(banco, lista(2, listOf(a1, a2)))
        listas.salvar(paciente, 2, pedido(emptyList(), listOf(a1.id)))
        online.servidor = lista(2, listOf(a1, a2))
        assertEquals(listOf(a2.id), listas.obter(paciente).alimentos.map { it.id })
        assertEquals("CONFLITO_VERSAO", assertFailsWith<ErroApi> { listas.copiarDe(paciente, Uuid.random(), 2) }.codigo)
        assertTrue(online.copias.isEmpty())
    }

    @Test
    fun versaoAntigaOuConflitoAbertoBloqueia() = comBanco { banco, _, listas ->
        semearDoPull(banco, lista(2, listOf(a1, a2)))
        assertEquals("CONFLITO_VERSAO", assertFailsWith<ErroApi> { listas.salvar(paciente, 1, pedido(emptyList(), listOf(a1.id))) }.codigo)
        listas.salvar(paciente, 2, pedido(emptyList(), listOf(a1.id)))
        val fonte = Fonte { l -> l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.CONFLITO, codigo = "CONFLITO_VERSAO",
            atual = JsonContrato.encodeToJsonElement(lista(3, listOf(a2)))) } }
        EnviadorFila(fonte, banco, { contexto }).enviar()
        assertTrue(listas.pendenteDeEnvio(paciente))
        assertEquals("CONFLITO_VERSAO", assertFailsWith<ErroApi> { listas.salvar(paciente, 3, pedido(emptyList(), emptyList())) }.codigo)
    }

    @Test
    fun rejeicaoDevolveAListaAnterior() = comBanco { banco, _, listas ->
        semearDoPull(banco, lista(2, listOf(a1, a2)))
        listas.salvar(paciente, 2, pedido(emptyList(), listOf(a1.id)))
        EnviadorFila(Fonte { l -> l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.REJEITADA, codigo = "ASSINATURA_INATIVA") } }, banco, { contexto }).enviar()
        assertFalse(listas.pendenteDeEnvio(paciente))
        assertTrue(fila(banco).none { it.status == "PENDENTE" })
        val depois = listas.obter(paciente)
        assertEquals(2, depois.versao); assertEquals(listOf(a1.id, a2.id), depois.alimentos.map { it.id })
    }

    @Test
    fun aceitaAListaDoServidorVoltaAValer() = comBanco { banco, online, listas ->
        semearDoPull(banco, lista(2, listOf(a1, a2)))
        listas.salvar(paciente, 2, pedido(emptyList(), listOf(a1.id)))
        val fonte = Fonte { l -> l.map { ResultadoMutacaoResposta(it.idMutacao, StatusMutacao.APLICADA, versao = 3) } }
        EnviadorFila(fonte, banco, { contexto }).enviar()
        assertEquals("listas", fonte.lotes.single().single().entidade)
        assertFalse(listas.pendenteDeEnvio(paciente))
        online.servidor = lista(3, listOf(a2), removidos = listOf(a1.id))
        assertEquals(online.servidor, listas.obter(paciente))
        // sem rede depois, vale a versão aceita
        online.servidor = null
        assertEquals(3, listas.obter(paciente).versao)
    }

    @Test
    fun semRedeESemNadaGuardadoAFalhaContinua() = comBanco { _, _, listas ->
        assertFailsWith<FalhaComunicacao> { listas.obter(paciente) }
        assertEquals("RECURSO_NAO_ENCONTRADO", assertFailsWith<ErroApi> { listas.salvar(paciente, 1, pedido(emptyList(), emptyList())) }.codigo)
    }
}
