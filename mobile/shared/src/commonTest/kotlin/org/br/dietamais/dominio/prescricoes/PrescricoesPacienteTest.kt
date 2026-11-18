package org.br.dietamais.dominio.prescricoes

import kotlinx.datetime.*
import org.br.dietamais.data.remoto.dto.OrientacaoResposta
import org.br.dietamais.data.remoto.dto.SuplementoResposta
import org.br.dietamais.dominio.rotina.JanelaSilencio
import org.br.dietamais.dominio.rotina.Lembrete
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PrescricoesPacienteTest {
    private val fuso = TimeZone.UTC
    private val t = Instant.parse("2026-11-01T00:00:00Z")
    private val agora = Instant.parse("2026-11-18T12:00:00Z")
    private fun sup(nome: String, horarios: List<String> = emptyList(), inicio: LocalDate = LocalDate(2026, 11, 1), fim: LocalDate? = null) =
        SuplementoResposta(Uuid.random(), Uuid.random(), nome = nome, dose = "1", horarios = horarios, inicio = inicio, fim = fim, versao = 1, criadoEm = t, atualizadoEm = t)

    @Test fun vigentesSoOsDoPeriodoEmOrdemDeHorario() {
        val futuro = sup("Futuro", inicio = LocalDate(2026, 12, 1))
        val acabou = sup("Acabou", fim = LocalDate(2026, 11, 17))
        val noite = sup("Ômega", listOf("20:00"))
        val manha = sup("Vitamina", listOf("08:00"))
        val semHora = sup("Ferro")
        val terminaHoje = sup("Zinco", listOf("12:00"), fim = LocalDate(2026, 11, 18))
        assertEquals(listOf("Vitamina", "Zinco", "Ômega", "Ferro"),
            suplementosVigentes(listOf(futuro, acabou, noite, manha, semHora, terminaHoje), LocalDate(2026, 11, 18)).map { it.nome })
    }

    @Test fun horariosIgnoramOQueNaoEHora() {
        assertEquals(listOf(LocalTime(8, 0), LocalTime(20, 0)), horariosDe(sup("X", listOf("20:00", "manhã", "08:00", "08:00"))))
    }

    @Test fun proximaTomadaHojeAmanhaOuNenhuma() {
        assertEquals(Instant.parse("2026-11-18T16:00:00Z"), proximaTomada(sup("C", listOf("08:00", "16:00")), agora, fuso))
        assertEquals(Instant.parse("2026-11-19T08:00:00Z"), proximaTomada(sup("V", listOf("08:00")), agora, fuso))
        assertNull(proximaTomada(sup("V", listOf("08:00"), fim = LocalDate(2026, 11, 18)), agora, fuso))
        assertNull(proximaTomada(sup("F"), agora, fuso))
    }

    @Test fun lembretesRespeitamPeriodoSilencioEDesligados() {
        val v = sup("V", listOf("08:00", "23:00"), fim = LocalDate(2026, 11, 20))
        val desligado = sup("D", listOf("09:00"))
        val l = planejarLembretesSuplemento(listOf(v, desligado), agora, fuso, JanelaSilencio(true, LocalTime(22, 0), LocalTime(7, 0)), setOf(desligado.id))
        // 08:00 de 19 e 20; 08:00 de hoje já passou; 23:00 cai no silêncio
        assertEquals(listOf(Instant.parse("2026-11-19T08:00:00Z"), Instant.parse("2026-11-20T08:00:00Z")), l.map { it.quando })
        assertTrue(l.all { it.refeicaoId == v.id && it.dietaId == null && it.dia == null })
        assertEquals(l.size, l.map { it.chave }.toSet().size)
    }

    @Test fun juntarOrdenaECortaNoLimite() {
        val r = Lembrete("r", Instant.parse("2026-11-19T10:00:00Z"), Uuid.random(), "Almoço")
        val s1 = Lembrete("s1", Instant.parse("2026-11-19T08:00:00Z"), Uuid.random(), "")
        val s2 = Lembrete("s2", Instant.parse("2026-11-19T12:00:00Z"), Uuid.random(), "")
        assertEquals(listOf("s1", "r"), juntarLembretes(listOf(r), listOf(s1, s2), limite = 2).map { it.chave })
    }

    @Test fun orientacoesSemArquivadasDaMaisNova() {
        fun o(titulo: String, dia: Int, arquivada: Boolean = false) = OrientacaoResposta(Uuid.random(), Uuid.random(), titulo, "x",
            if (arquivada) t else null, 1, Instant.parse("2026-11-${dia.toString().padStart(2, '0')}T10:00:00Z"), t)
        assertEquals(listOf("Nova", "Velha"), orientacoesVisiveis(listOf(o("Velha", 3), o("Arquivada", 10, true), o("Nova", 18))).map { it.titulo })
    }

    @Test fun textoComNegritoEListaSemOutraMarcacao() {
        val blocos = lerTextoOrientacao("Para montar o prato:\n\n- **Metade** com verduras\n* Um quarto com arroz\n# título fica texto\nSem **fechar")
        assertEquals(5, blocos.size)
        assertEquals(BlocoTexto.Paragrafo(listOf(TrechoTexto("Para montar o prato:"))), blocos[0])
        assertEquals(BlocoTexto.ItemLista(listOf(TrechoTexto("Metade", true), TrechoTexto(" com verduras"))), blocos[1])
        assertEquals(BlocoTexto.ItemLista(listOf(TrechoTexto("Um quarto com arroz"))), blocos[2])
        assertEquals(BlocoTexto.Paragrafo(listOf(TrechoTexto("# título fica texto"))), blocos[3])
        assertEquals(BlocoTexto.Paragrafo(listOf(TrechoTexto("Sem **fechar"))), blocos[4])
    }
}
