package org.br.dietamais.dominio.metas

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.FrequenciaMeta
import org.br.dietamais.data.remoto.dto.MetaResposta
import org.br.dietamais.data.remoto.dto.RegistroMetaResposta
import org.br.dietamais.data.remoto.dto.TipoMeta
import org.br.dietamais.dominio.rotina.JanelaSilencio
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class LembretesMetaTest {
    // terça, 17/11/2026, 10h UTC
    private val agora = Instant.parse("2026-11-17T10:00:00Z")
    private val hoje = LocalDate(2026, 11, 17)
    private val criada = Instant.parse("2026-11-01T00:00:00Z")
    private fun meta(tipo: TipoMeta = TipoMeta.ATIVIDADE_MIN, alvo: Double = 30.0, frequencia: FrequenciaMeta = FrequenciaMeta.DIARIA, vezes: Int? = null,
        inicio: LocalDate? = null, fim: LocalDate? = null) =
        MetaResposta(Uuid.random(), Uuid.random(), tipo, null, alvo, null, frequencia, vezes, inicio, fim, versao = 1, criadoEm = criada, atualizadoEm = criada)
    private fun reg(m: MetaResposta, dia: LocalDate, valor: Double) = RegistroMetaResposta(Uuid.random(), m.id, dia, valor, criada)
    private fun h(hora: Int, min: Int = 0) = LocalTime(hora, min)

    @Test fun leHorariosEmVariosFormatos() {
        assertEquals(listOf(h(9), h(13), h(17, 30)), lerHorariosMeta("17:30, 9:00; 13h"))
        assertEquals(listOf(h(9, 30)), lerHorariosMeta("09h30 9:30"))
        assertEquals(listOf(h(7)), lerHorariosMeta("7"))
        assertNull(lerHorariosMeta(""))
        assertNull(lerHorariosMeta("25:00"))
        assertNull(lerHorariosMeta("9:00, meio-dia"))
        assertNull(lerHorariosMeta("6, 9, 12, 15, 18"))
        assertEquals("9:00, 13:05", textoHorariosMeta(listOf(h(9), h(13, 5))))
    }

    @Test fun sugereHorariosPorTipo() {
        assertEquals(listOf(h(9), h(13), h(17)), horariosPadraoMeta(TipoMeta.AGUA))
        assertEquals(listOf(h(20)), horariosPadraoMeta(TipoMeta.SONO_HORAS))
    }

    @Test fun soMetasLigadasNosDiasDaMetaForaDoSilencioEDoPassado() {
        val ligada = meta(fim = LocalDate(2026, 11, 19)); val desligada = meta()
        val l = planejarLembretesMeta(listOf(ligada, desligada), emptyList(), mapOf(ligada.id to listOf(h(9), h(20), h(23))),
            agora, TimeZone.UTC, JanelaSilencio())
        // 9h de hoje já passou; 23h está no silêncio; a meta acaba no dia 19
        assertEquals(listOf("2026-11-17T20:00:00Z", "2026-11-18T09:00:00Z", "2026-11-18T20:00:00Z", "2026-11-19T09:00:00Z", "2026-11-19T20:00:00Z").map(Instant::parse),
            l.map { it.quando })
        assertTrue(l.all { it.refeicaoId == ligada.id && it.chave.startsWith("meta-${ligada.id}-") && it.dietaId == null })
    }

    @Test fun metaQueAindaNaoComecouSoLembraAPartirDoInicio() {
        val m = meta(inicio = LocalDate(2026, 11, 22))
        val l = planejarLembretesMeta(listOf(m), emptyList(), mapOf(m.id to listOf(h(20))), agora, TimeZone.UTC)
        assertEquals(LocalDate(2026, 11, 22).toString(), l.first().chave.split('-').drop(6).take(3).joinToString("-"))
        assertEquals(2, l.size) // dias 22 e 23 dentro dos 7 dias
    }

    @Test fun diaJaCumpridoNaoLembra() {
        val m = meta()
        val l = planejarLembretesMeta(listOf(m), listOf(reg(m, hoje, 20.0), reg(m, hoje, 10.0)), mapOf(m.id to listOf(h(20))), agora, TimeZone.UTC, dias = 2)
        assertEquals(listOf(Instant.parse("2026-11-18T20:00:00Z")), l.map { it.quando })
    }

    @Test fun semanaJaCompletaNaoLembraAteASegunda() {
        val m = meta(frequencia = FrequenciaMeta.SEMANAL, vezes = 2)
        val segunda = LocalDate(2026, 11, 16)
        val l = planejarLembretesMeta(listOf(m), listOf(reg(m, segunda, 30.0), reg(m, hoje, 30.0)), mapOf(m.id to listOf(h(20))), agora, TimeZone.UTC)
        // terça a domingo já cumpridos; a próxima semana começa na segunda, 23
        assertEquals(listOf(Instant.parse("2026-11-23T20:00:00Z")), l.map { it.quando })
    }
}
