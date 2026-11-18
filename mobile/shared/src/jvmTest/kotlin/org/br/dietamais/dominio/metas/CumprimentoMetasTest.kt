package org.br.dietamais.dominio.metas

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.*
import org.br.dietamais.data.remoto.dto.*
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Roda `contrato/calculos/metas.json`, o mesmo arquivo do backend (RN-MET-03), e confere o progresso do Hoje e da semana (MOB-90). */
class CumprimentoMetasTest {
    private fun ler() = Json.parseToJsonElement(
        CumprimentoMetasTest::class.java.getResourceAsStream("/contrato/calculos/metas.json")!!.bufferedReader(Charsets.UTF_8).use { it.readText() },
    ).jsonObject

    private fun JsonObject.texto(campo: String) = get(campo)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    @Test
    fun todosOsVetoresBatem() {
        val casos = ler().getValue("casos").jsonArray.map { it.jsonObject }
        assertTrue(casos.size >= 11, "esperava os vetores completos")
        val falhas = mutableListOf<String>()
        for (c in casos) {
            val id = c.texto("id")!!
            runCatching {
                val e = c.getValue("entrada").jsonObject; val meta = e.getValue("meta").jsonObject; val janela = e.getValue("janela").jsonObject
                val soma = mutableMapOf<LocalDate, Double>()
                e.getValue("registros").jsonArray.map { it.jsonObject }.forEach { r ->
                    val d = LocalDate.parse(r.texto("data")!!); soma[d] = (soma[d] ?: 0.0) + r.texto("valor")!!.toDouble()
                }
                val r = cumprimento(FrequenciaMeta.valueOf(meta.texto("frequencia")!!), meta.texto("alvo")!!.toDouble(), meta.texto("vezesPorSemana")?.toInt(),
                    meta.texto("inicio")?.let(LocalDate::parse), meta.texto("fim")?.let(LocalDate::parse),
                    LocalDate.parse(janela.texto("de")!!), LocalDate.parse(janela.texto("ate")!!), LocalDate.parse(e.texto("hoje")!!), soma)
                val esp = c.getValue("esperado").jsonObject
                val real = listOf(r.diasPrevistos, r.diasCumpridos, r.percentual, r.sequenciaAtual, r.melhorSequencia)
                val esperado = listOf(esp.texto("diasPrevistos")!!.toInt(), esp.texto("diasCumpridos")!!.toInt(), esp.texto("percentual")!!.toDouble(),
                    esp.texto("sequenciaAtual")!!.toInt(), esp.texto("melhorSequencia")!!.toInt())
                assertEquals(esperado, real)
            }.onFailure { falhas += "$id: ${it.message}" }
        }
        assertTrue(falhas.isEmpty(), falhas.joinToString("\n"))
    }

    @Test
    fun percentualArredondaMeioParaCima() {
        assertEquals(85.7, percentual(6, 7))
        assertEquals(66.7, percentual(2, 3))
        assertEquals(0.0, percentual(0, 0))
        assertEquals(100.0, percentual(3, 3))
    }

    @Test
    fun segundaDaSemana() {
        assertEquals(LocalDate(2026, 11, 16), segundaDe(LocalDate(2026, 11, 17)))
        assertEquals(LocalDate(2026, 11, 16), segundaDe(LocalDate(2026, 11, 16)))
        assertEquals(LocalDate(2026, 11, 16), segundaDe(LocalDate(2026, 11, 22)))
    }

    private val agora = Instant.parse("2026-11-17T12:00:00Z")
    private fun meta(frequencia: FrequenciaMeta = FrequenciaMeta.DIARIA, alvo: Double = 30.0, vezes: Int? = null, inicio: LocalDate? = null) =
        MetaResposta(Uuid.random(), Uuid.random(), TipoMeta.ATIVIDADE_MIN, alvo = alvo, frequencia = frequencia, vezesPorSemana = vezes, inicio = inicio,
            versao = 1, criadoEm = agora, atualizadoEm = agora)
    private fun reg(m: MetaResposta, dia: Int, valor: Double) = RegistroMetaResposta(Uuid.random(), m.id, LocalDate(2026, 11, dia), valor, agora)

    @Test
    fun progressoDiarioSomaHojeSequenciaEDiasDaSemana() {
        val m = meta()
        val outra = meta()
        val registros = listOf(reg(m, 13, 30.0), reg(m, 14, 30.0), reg(m, 15, 40.0), reg(m, 16, 10.0), reg(m, 16, 20.0), reg(m, 17, 10.0), reg(outra, 17, 90.0))
        val p = progressoMeta(m, registros, LocalDate(2026, 11, 17))
        assertEquals(10.0, p.hoje)
        assertFalse(p.cumpridaHoje)
        // 13, 14, 15 e 16 cumpridos; hoje ainda aberto não quebra
        assertEquals(4, p.sequencia)
        assertEquals(listOf(SituacaoDia.CUMPRIDO, SituacaoDia.ABERTO, SituacaoDia.FUTURO, SituacaoDia.FUTURO, SituacaoDia.FUTURO, SituacaoDia.FUTURO, SituacaoDia.FUTURO),
            p.dias.map { it.second })
        assertEquals(2, p.semana.diasPrevistos)
        assertEquals(1, p.semana.diasCumpridos)
    }

    @Test
    fun progressoSemanalContaQuantasFaltam() {
        val m = meta(FrequenciaMeta.SEMANAL, vezes = 3)
        val p = progressoMeta(m, listOf(reg(m, 16, 30.0)), LocalDate(2026, 11, 17))
        assertEquals(1, p.semana.diasCumpridos)
        assertEquals(2, p.faltamNaSemana)
        assertEquals(0, p.sequencia)
    }

    @Test
    fun diaAntesDoInicioFicaForaDoPeriodo() {
        val m = meta(inicio = LocalDate(2026, 11, 17))
        val p = progressoMeta(m, emptyList(), LocalDate(2026, 11, 17))
        assertEquals(SituacaoDia.FORA, p.dias.first().second)
        assertEquals(SituacaoDia.ABERTO, p.dias[1].second)
        assertEquals(0, p.sequencia)
    }
}
