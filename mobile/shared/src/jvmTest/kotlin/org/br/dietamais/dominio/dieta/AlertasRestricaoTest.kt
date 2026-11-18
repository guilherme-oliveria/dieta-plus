package org.br.dietamais.dominio.dieta

import kotlinx.serialization.json.*
import org.br.dietamais.data.remoto.dto.RestricaoSaudeDto
import org.br.dietamais.data.remoto.dto.TipoRestricaoSaude
import kotlin.test.*
import kotlin.uuid.Uuid

/** Roda `contrato/calculos/alertas-restricao.json` (mesmo arquivo do backend, RN-DIE-14). */
class AlertasRestricaoTest {
    private val vetores = Json.parseToJsonElement(
        AlertasRestricaoTest::class.java.getResourceAsStream("/contrato/calculos/alertas-restricao.json")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }).jsonObject

    private fun textos(e: JsonElement?) = e?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

    @Test fun vetoresDoContrato() {
        val casos = vetores.getValue("casos").jsonArray.map { it.jsonObject }
        assertTrue(casos.size >= 6)
        for (caso in casos) {
            val id = caso.getValue("id").jsonPrimitive.content
            assertEquals("alertasItem", caso.getValue("funcao").jsonPrimitive.content, id)
            val entrada = caso.getValue("entrada").jsonObject
            val restricoes = entrada.getValue("restricoes").jsonArray.map { it.jsonObject }.map { r ->
                RestricaoSaudeDto(Uuid.parse(r.getValue("id").jsonPrimitive.content), TipoRestricaoSaude.valueOf(r.getValue("tipo").jsonPrimitive.content),
                    r.getValue("nome").jsonPrimitive.content, marcadores = textos(r["marcadores"]))
            }
            val real = AlertasRestricao.alertasItem(restricoes, textos(entrada.getValue("item").jsonObject["marcadores"]))
            val esperado = caso.getValue("esperado").jsonObject.getValue("alertas").jsonArray.map { it.jsonObject }
            assertEquals(esperado.size, real.size, id)
            esperado.zip(real).forEach { (e, a) ->
                assertEquals(Uuid.parse(e.getValue("restricaoId").jsonPrimitive.content), a.restricaoId, id)
                assertEquals(e.getValue("tipo").jsonPrimitive.content, a.tipo.name, id)
                assertEquals(e.getValue("marcador").jsonPrimitive.content, a.marcador, id)
                assertEquals(e.getValue("exigeConfirmacao").jsonPrimitive.boolean, a.exigeConfirmacao, id)
            }
        }
    }

    @Test fun relevantesIgnoraCondicaoEMedicamentoESemMarcador() {
        val alergia = RestricaoSaudeDto(Uuid.random(), TipoRestricaoSaude.ALERGIA, "Amendoim", marcadores = listOf("AMENDOIM"))
        val semMarcador = RestricaoSaudeDto(Uuid.random(), TipoRestricaoSaude.INTOLERANCIA, "Outra")
        val condicao = RestricaoSaudeDto(Uuid.random(), TipoRestricaoSaude.CONDICAO, "Hipertensão")
        assertEquals(listOf(alergia), AlertasRestricao.relevantes(listOf(condicao, alergia, semMarcador)))
    }
}
