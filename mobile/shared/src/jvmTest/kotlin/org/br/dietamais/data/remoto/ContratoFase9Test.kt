package org.br.dietamais.data.remoto

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import org.br.dietamais.data.remoto.dto.*
import kotlin.test.*

/**
 * MOB-81: os exemplos da 1.4.0 (BE-C9, mesmos arquivos de `backend/src/test/resources/contrato/fase-9/`) cabem nos DTOs do app,
 * toleram campo novo e enum desconhecido, e os vetores compartilhados têm o formato esperado.
 */
class ContratoFase9Test {
    private val exemplos: Map<String, KSerializer<*>> = mapOf(
        "restricaoSaude.json" to RestricaoSaudeDto.serializer(), "alertaRestricao.json" to AlertaRestricaoDto.serializer(),
        "preferenciasAgenda-requisicao.json" to PreferenciasAgendaDto.serializer(), "preferenciasAgenda-resposta.json" to PreferenciasAgendaDto.serializer(),
        "consulta-requisicao.json" to CriarConsultaRequisicao.serializer(), "consulta-resposta.json" to ConsultaResposta.serializer(),
        "consultaAtualizar-requisicao.json" to AtualizarConsultaRequisicao.serializer(), "consultaPaciente-resposta.json" to ConsultaResposta.serializer(),
        "registroForaDoPlano-requisicao.json" to RegistroForaDoPlanoRequisicao.serializer(), "registroForaDoPlano-resposta.json" to RegistroForaDoPlanoResposta.serializer(),
        "humorAdesao.json" to HumorAdesaoDto.serializer(),
        "meta-requisicao.json" to MetaRequisicao.serializer(), "meta-resposta.json" to MetaResposta.serializer(),
        "atualizarMeta-requisicao.json" to AtualizarMetaRequisicao.serializer(), "metas-resposta.json" to MetasResposta.serializer(),
        "registroMeta-requisicao.json" to RegistroMetaRequisicao.serializer(), "registroMeta-resposta.json" to RegistroMetaResposta.serializer(),
        "modeloMeta-requisicao.json" to ModeloMetaRequisicao.serializer(), "modeloMeta-resposta.json" to ModeloMetaResposta.serializer(),
        "modelosMeta-resposta.json" to ModelosMetaResposta.serializer(), "cumprimentoMetas-resposta.json" to CumprimentoMetasResposta.serializer(),
        "suplemento-requisicao.json" to SuplementoRequisicao.serializer(), "suplemento-resposta.json" to SuplementoResposta.serializer(),
        "atualizarSuplemento-requisicao.json" to AtualizarSuplementoRequisicao.serializer(), "suplementos-resposta.json" to SuplementosResposta.serializer(),
        "orientacao-requisicao.json" to OrientacaoRequisicao.serializer(), "orientacao-resposta.json" to OrientacaoResposta.serializer(),
        "atualizarOrientacao-requisicao.json" to AtualizarOrientacaoRequisicao.serializer(), "orientacoes-resposta.json" to OrientacoesResposta.serializer(),
        "modeloOrientacao-requisicao.json" to ModeloOrientacaoRequisicao.serializer(), "modeloOrientacao-resposta.json" to ModeloOrientacaoResposta.serializer(),
        "modelosOrientacao-resposta.json" to ModelosOrientacaoResposta.serializer(),
        "modeloDocumento-requisicao.json" to ModeloDocumentoRequisicao.serializer(), "modeloDocumento-resposta.json" to ModeloDocumentoResposta.serializer(),
        "modelosDocumento-resposta.json" to ModelosDocumentoResposta.serializer(), "emitirDocumento-requisicao.json" to EmitirDocumentoRequisicao.serializer(),
        "documento-resposta.json" to DocumentoResposta.serializer(), "documentos-resposta.json" to DocumentosResposta.serializer(),
        "anularDocumento-requisicao.json" to AnularDocumentoRequisicao.serializer(), "documentoPaciente-resposta.json" to DocumentoPacienteResposta.serializer(),
        "documentosPaciente-resposta.json" to DocumentosPacienteResposta.serializer(), "conferenciaDocumento-resposta.json" to ConferenciaDocumentoResposta.serializer(),
        "erro-alergia-nao-confirmada.json" to ProblemaResposta.serializer(), "erro-transicao-status-invalida.json" to ProblemaResposta.serializer(),
        "erro-link-invalido.json" to ProblemaResposta.serializer(), "erro-meta-invalida.json" to ProblemaResposta.serializer(),
        "erro-documento-ja-anulado.json" to ProblemaResposta.serializer(),
    )

    @Test
    fun todosOsExemplosCabemNosDtosEToleramCampoNovo() {
        val arquivos = javaClass.getResource("/contrato/fase-9")!!.let { java.io.File(it.toURI()).list()!!.toSet() }
        assertEquals(arquivos, exemplos.keys, "todo exemplo da fase 9 tem DTO no app")
        exemplos.forEach { (arquivo, serializer) ->
            val objeto = JsonContrato.parseToJsonElement(recursoContrato("fase-9/$arquivo")).jsonObject
            val original = JsonContrato.decodeFromJsonElement(serializer, objeto)
            val futuro = JsonObject(objeto + ("campoFuturo" to buildJsonObject { put("versao", 2) }))
            assertEquals(original, JsonContrato.decodeFromJsonElement(serializer, futuro), arquivo)
        }
    }

    @Test
    fun codigosDeErroNovosChegamPeloCodigo() {
        mapOf("erro-alergia-nao-confirmada.json" to "ALERGIA_NAO_CONFIRMADA", "erro-transicao-status-invalida.json" to "TRANSICAO_STATUS_INVALIDA",
            "erro-link-invalido.json" to "LINK_INVALIDO", "erro-meta-invalida.json" to "META_INVALIDA", "erro-documento-ja-anulado.json" to "DOCUMENTO_JA_ANULADO")
            .forEach { (arquivo, codigo) -> assertEquals(codigo, JsonContrato.decodeFromString<ProblemaResposta>(recursoContrato("fase-9/$arquivo")).codigo, arquivo) }
    }

    @Test
    fun enumsDesconhecidosViramOutro() {
        val consulta = JsonContrato.decodeFromString<ConsultaResposta>(recursoContrato("fase-9/consulta-resposta.json")
            .replace("\"RETORNO\"", "\"TIPO_FUTURO\"").replace("\"AGENDADA\"", "\"STATUS_FUTURO\"").replace("\"ONLINE\"", "\"MODALIDADE_FUTURA\""))
        assertEquals(TipoConsulta.OUTRO, consulta.tipo); assertEquals(StatusConsulta.OUTRO, consulta.status); assertEquals(ModalidadeConsulta.OUTRO, consulta.modalidade)
        val meta = JsonContrato.decodeFromString<MetaResposta>(recursoContrato("fase-9/meta-resposta.json").replace("\"AGUA\"", "\"META_FUTURA\"").replace("\"DIARIA\"", "\"MENSAL\""))
        assertEquals(TipoMeta.OUTRO, meta.tipo); assertEquals(FrequenciaMeta.OUTRO, meta.frequencia)
        val restricao = JsonContrato.decodeFromString<RestricaoSaudeDto>(recursoContrato("fase-9/restricaoSaude.json").replace("\"ALERGIA\"", "\"X\"").replace("\"GRAVE\"", "\"Y\""))
        assertEquals(TipoRestricaoSaude.OUTRO, restricao.tipo); assertEquals(GravidadeRestricao.OUTRO, restricao.gravidade)
        val documento = JsonContrato.decodeFromString<ConferenciaDocumentoResposta>(recursoContrato("fase-9/conferenciaDocumento-resposta.json")
            .replace("\"ATESTADO\"", "\"RECEITA\"").replace("\"VALIDO\"", "\"SUSPENSO\""))
        assertEquals(TipoDocumento.OUTRO, documento.tipo); assertEquals(SituacaoDocumento.OUTRO, documento.situacao)
    }

    @Test
    fun pacienteVeConsultaEncerradaSemObservacaoInterna() {
        val c = JsonContrato.decodeFromString<ConsultaResposta>(recursoContrato("fase-9/consultaPaciente-resposta.json"))
        assertEquals(StatusConsulta.ENCERRADA, c.status); assertNull(c.observacaoInterna); assertEquals(ModalidadeConsulta.ONLINE, c.modalidade)
    }

    @Test
    fun syncDaFase9GuardaAsColecoesNovasEOBackendAntigoContinuaValendo() {
        val antigo = JsonContrato.decodeFromString<SyncResposta>("""{"cursor":"1","temMais":false,"mudancas":{"pacientes":[],"vinculos":[],"nutris":[]}}""")
        assertEquals(0, antigo.mudancas.total)
        val meta = recursoContrato("fase-9/meta-resposta.json"); val registro = recursoContrato("fase-9/registroMeta-resposta.json")
        val suplemento = recursoContrato("fase-9/suplemento-resposta.json"); val orientacao = recursoContrato("fase-9/orientacao-resposta.json")
        val fora = recursoContrato("fase-9/registroForaDoPlano-resposta.json"); val documento = recursoContrato("fase-9/documentoPaciente-resposta.json")
        fun mudanca(json: String) = """{"id":${JsonContrato.parseToJsonElement(json).jsonObject.getValue("id")},"versao":1,"excluido":false,"dados":$json}"""
        val novo = JsonContrato.decodeFromString<SyncResposta>("""{"cursor":"2","temMais":false,"mudancas":{"pacientes":[],"vinculos":[],"nutris":[],
            "metas":[${mudanca(meta)}],"registrosMeta":[${mudanca(registro)}],"prescricoesSuplemento":[${mudanca(suplemento)}],
            "orientacoes":[${mudanca(orientacao)}],"registrosForaDoPlano":[${mudanca(fora)}],"documentos":[${mudanca(documento)}]}}""")
        assertEquals(6, novo.mudancas.total)
        assertEquals(true, novo.mudancas.metas.single().dados!!.alvoPadrao)
    }

    @Test
    fun motorDaFase9ServeOsExemplosPelaRota() = kotlinx.coroutines.test.runTest {
        io.ktor.client.HttpClient(motorFase9()).use { cliente ->
            val metas = cliente.get("https://app.example.invalid/api/v1/pacientes/019b0000-0000-7000-8000-000000000006/metas")
            assertEquals(io.ktor.http.HttpStatusCode.OK, metas.status)
            assertEquals(1, JsonContrato.decodeFromString<MetasResposta>(metas.bodyAsText()).itens.size)
            val conferencia = cliente.get("https://app.example.invalid/api/v1/documentos/conferir/K7M2-Q9XP-4HTR")
            assertEquals(SituacaoDocumento.VALIDO, JsonContrato.decodeFromString<ConferenciaDocumentoResposta>(conferencia.bodyAsText()).situacao)
            assertEquals(io.ktor.http.HttpStatusCode.NotFound, cliente.get("https://app.example.invalid/api/v1/rota-inexistente").status)
        }
    }

    @Test
    fun vetoresCompartilhadosTemOFormatoEsperado() {
        listOf("calculos/alertas-restricao.json" to setOf("alertasItem"), "calculos/metas.json" to setOf("cumprimento")).forEach { (arquivo, funcoes) ->
            val raiz = JsonContrato.parseToJsonElement(recursoContrato(arquivo)).jsonObject
            val casos = raiz.getValue("casos").jsonArray.map { it.jsonObject }
            assertTrue(casos.isNotEmpty(), arquivo)
            assertEquals(casos.size, casos.map { it.getValue("id").jsonPrimitive.content }.toSet().size, "id repetido em $arquivo")
            casos.forEach { caso ->
                assertTrue(caso.getValue("funcao").jsonPrimitive.content in funcoes, "$arquivo: ${caso["id"]}")
                assertTrue("entrada" in caso && "esperado" in caso, "$arquivo: ${caso["id"]}")
            }
        }
    }
}
