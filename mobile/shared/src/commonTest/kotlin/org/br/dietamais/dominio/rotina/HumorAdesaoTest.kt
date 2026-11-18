package org.br.dietamais.dominio.rotina

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.JanelaAdesaoDto
import org.br.dietamais.data.remoto.dto.RegistroForaDoPlanoResposta
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class HumorAdesaoTest {
    @Test fun nivelDaMediaArredondaEFicaNaEscala() {
        assertEquals(4, nivelDaMedia(3.8)); assertEquals(3, nivelDaMedia(2.7)); assertEquals(3, nivelDaMedia(2.5))
        assertEquals(1, nivelDaMedia(0.2)); assertEquals(5, nivelDaMedia(7.0))
        assertNull(nivelDaMedia(null)); assertNull(nivelDaMedia(Double.NaN))
    }

    @Test fun mediaComUmaCasaEVirgula() {
        assertEquals("3,8", mediaUmaCasa(3.75)); assertEquals("2,7", mediaUmaCasa(2.67)); assertEquals("5,0", mediaUmaCasa(5.0))
    }

    @Test fun registradasSomaFeitasTrocadasEPuladas() {
        assertEquals(31, registradas(JanelaAdesaoDto(7, 42, 25, 4, 2, 69.0)))
    }

    @Test fun periodoTerminaHojeNoFusoDaNutri() {
        val agora = Instant.parse("2026-11-17T01:00:00Z") // ainda 16/11 em São Paulo
        assertEquals(LocalDate(2026, 11, 10) to LocalDate(2026, 11, 16), periodoDaJanela(7, agora, TimeZone.of("America/Sao_Paulo")))
        assertEquals(LocalDate(2026, 11, 17) to LocalDate(2026, 11, 17), periodoDaJanela(1, agora, TimeZone.UTC))
    }

    @Test fun foraDoPlanoDoMaisNovoAoMaisAntigoNaHoraLocal() {
        val a = RegistroForaDoPlanoResposta(Uuid.random(), LocalDate(2026, 11, 10), "Hambúrguer", null, Instant.parse("2026-11-10T16:00:00Z"))
        val b = RegistroForaDoPlanoResposta(Uuid.random(), LocalDate(2026, 11, 14), "Bolo", null, Instant.parse("2026-11-14T18:30:00Z"))
        val r = foraDoPlanoOrdenado(listOf(a, b), TimeZone.of("America/Sao_Paulo"))
        assertEquals(listOf("Bolo", "Hambúrguer"), r.map { it.first.texto })
        assertEquals(15, r[0].second.hour); assertEquals(30, r[0].second.minute)
        assertTrue(foraDoPlanoOrdenado(null, TimeZone.UTC).isEmpty())
    }
}
