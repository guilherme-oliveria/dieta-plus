package org.br.dietamais.data.remoto

import io.ktor.client.engine.mock.*
import io.ktor.http.*

/**
 * MOB-81: `MockEngine` com os exemplos da 1.4.0 (`contrato/fase-9/`), para as telas da fase testarem antes de o endpoint existir.
 * Rota sem exemplo responde 404 com ProblemDetail. `rotas` acrescenta ou troca respostas (método + caminho sem `/api/v1`).
 */
internal fun motorFase9(rotas: Map<Pair<HttpMethod, String>, Pair<HttpStatusCode, String>> = emptyMap()): MockEngine {
    val json = headersOf(HttpHeaders.ContentType, "application/json")
    val problema = headersOf(HttpHeaders.ContentType, "application/problem+json")
    fun ex(arquivo: String) = HttpStatusCode.OK to recursoContrato("fase-9/$arquivo")
    fun criado(arquivo: String) = HttpStatusCode.Created to recursoContrato("fase-9/$arquivo")
    val id = Regex("[0-9a-f-]{36}")
    val padrao: Map<Pair<HttpMethod, String>, Pair<HttpStatusCode, String>> = mapOf(
        (HttpMethod.Get to "/pacientes/{id}/metas") to ex("metas-resposta.json"),
        (HttpMethod.Post to "/pacientes/{id}/metas") to criado("meta-resposta.json"),
        (HttpMethod.Patch to "/metas/{id}") to ex("meta-resposta.json"),
        (HttpMethod.Get to "/pacientes/{id}/metas/cumprimento") to ex("cumprimentoMetas-resposta.json"),
        (HttpMethod.Post to "/eu/registros-meta") to criado("registroMeta-resposta.json"),
        (HttpMethod.Get to "/eu/nutri/modelos-meta") to ex("modelosMeta-resposta.json"),
        (HttpMethod.Post to "/eu/nutri/modelos-meta") to criado("modeloMeta-resposta.json"),
        (HttpMethod.Get to "/pacientes/{id}/suplementos") to ex("suplementos-resposta.json"),
        (HttpMethod.Post to "/pacientes/{id}/suplementos") to criado("suplemento-resposta.json"),
        (HttpMethod.Patch to "/suplementos/{id}") to ex("suplemento-resposta.json"),
        (HttpMethod.Get to "/pacientes/{id}/orientacoes") to ex("orientacoes-resposta.json"),
        (HttpMethod.Post to "/pacientes/{id}/orientacoes") to criado("orientacao-resposta.json"),
        (HttpMethod.Patch to "/orientacoes/{id}") to ex("orientacao-resposta.json"),
        (HttpMethod.Get to "/eu/nutri/modelos-orientacao") to ex("modelosOrientacao-resposta.json"),
        (HttpMethod.Post to "/eu/nutri/modelos-orientacao") to criado("modeloOrientacao-resposta.json"),
        (HttpMethod.Get to "/eu/nutri/modelos-documento") to ex("modelosDocumento-resposta.json"),
        (HttpMethod.Post to "/eu/nutri/modelos-documento") to criado("modeloDocumento-resposta.json"),
        (HttpMethod.Get to "/pacientes/{id}/documentos") to ex("documentos-resposta.json"),
        (HttpMethod.Post to "/pacientes/{id}/documentos") to criado("documento-resposta.json"),
        (HttpMethod.Get to "/eu/documentos") to ex("documentosPaciente-resposta.json"),
        (HttpMethod.Get to "/documentos/conferir/{id}") to ex("conferenciaDocumento-resposta.json"),
        (HttpMethod.Post to "/consultas") to criado("consulta-resposta.json"),
        (HttpMethod.Patch to "/consultas/{id}") to ex("consulta-resposta.json"),
        (HttpMethod.Get to "/eu/nutri/preferencias-agenda") to ex("preferenciasAgenda-resposta.json"),
        (HttpMethod.Put to "/eu/nutri/preferencias-agenda") to ex("preferenciasAgenda-resposta.json"),
        (HttpMethod.Post to "/eu/registros-fora-do-plano") to criado("registroForaDoPlano-resposta.json"),
    ) + rotas
    return MockEngine { req ->
        val caminho = req.url.encodedPath.removePrefix("/api/v1").replace(Regex("/documentos/conferir/[^/]+"), "/documentos/conferir/{id}").replace(id, "{id}")
        val (status, corpo) = padrao[req.method to caminho]
            ?: (HttpStatusCode.NotFound to """{"type":"about:blank","title":"Não encontrado","status":404,"detail":"Sem exemplo","codigo":"RECURSO_NAO_ENCONTRADO","campos":[]}""")
        respond(corpo, status, if (status.isSuccess()) json else problema)
    }
}
