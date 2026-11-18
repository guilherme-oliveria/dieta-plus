package org.br.dietamais.dominio.metas

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.AguaResposta
import org.br.dietamais.data.remoto.dto.FrequenciaMeta
import org.br.dietamais.data.remoto.dto.MetaResposta
import org.br.dietamais.data.remoto.dto.RegistroMetaResposta
import org.br.dietamais.data.remoto.dto.TipoMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AguaComoMetaTest {
    private val hoje = LocalDate(2026, 11, 18)
    private val paciente = Uuid.random()
    private fun meta(tipo: TipoMeta = TipoMeta.AGUA, alvo: Double = 2450.0, criada: String = "2026-11-17T00:00:00Z", inicio: LocalDate? = null) =
        MetaResposta(Uuid.random(), paciente, tipo, null, alvo, null, FrequenciaMeta.DIARIA, null, inicio, null, true, 1,
            Instant.parse(criada), Instant.parse(criada))
    private fun copo(ml: Int, quando: String, id: Uuid = Uuid.random()) = AguaResposta(id, paciente, ml, Instant.parse(quando))

    @Test fun escolheAMetaDeAguaMaisAntigaQueValeHoje() {
        val antiga = meta(criada = "2026-11-01T00:00:00Z"); val nova = meta(criada = "2026-11-10T00:00:00Z")
        val futura = meta(criada = "2026-10-01T00:00:00Z", inicio = LocalDate(2026, 12, 1))
        assertEquals(antiga.id, metaAguaAtual(listOf(nova, meta(TipoMeta.PASSOS, criada = "2026-10-01T00:00:00Z"), futura, antiga), hoje)?.id)
        assertNull(metaAguaAtual(listOf(meta(TipoMeta.SONO_HORAS)), hoje))
    }

    @Test fun alvoVemDaMetaESemElaDoPeso() {
        assertEquals(2450, alvoAguaMl(meta(alvo = 2450.4), 80.0))
        assertEquals(2800, alvoAguaMl(null, 80.0))
        assertEquals(2000, alvoAguaMl(null, null))
    }

    @Test fun semMetaSomaOCaminhoAntigoDoDia() {
        val agua = listOf(copo(300, "2026-11-18T10:00:00Z"), copo(200, "2026-11-17T23:00:00Z"))
        assertEquals(300, aguaGravadaMl(null, emptyList(), agua, emptySet(), hoje, TimeZone.UTC))
        // o fuso decide o dia: 23h UTC do dia 17 é 20h no Brasil, ainda dia 17
        assertEquals(200, aguaGravadaMl(null, emptyList(), agua, emptySet(), LocalDate(2026, 11, 17), TimeZone.of("America/Sao_Paulo")))
    }

    @Test fun comMetaNaoContaDuasVezesOQueOServidorEspelhou() {
        val m = meta(); val outra = meta()
        val naFila = Uuid.random()
        // o copo antigo já enviado volta como registro da meta; o que ainda está na fila só existe no caminho antigo
        val agua = listOf(copo(250, "2026-11-18T08:00:00Z"), copo(150, "2026-11-18T09:00:00Z", naFila))
        val registros = listOf(RegistroMetaResposta(Uuid.random(), m.id, hoje, 250.0, Instant.parse("2026-11-18T08:00:00Z")),
            RegistroMetaResposta(Uuid.random(), m.id, hoje, 500.0, Instant.parse("2026-11-18T11:00:00Z")),
            RegistroMetaResposta(Uuid.random(), outra.id, hoje, 750.0, Instant.parse("2026-11-18T11:00:00Z")),
            RegistroMetaResposta(Uuid.random(), m.id, LocalDate(2026, 11, 17), 900.0, Instant.parse("2026-11-17T11:00:00Z")))
        assertEquals(250 + 500 + 150, aguaGravadaMl(m, registros, agua, setOf(naFila), hoje, TimeZone.UTC))
    }
}
