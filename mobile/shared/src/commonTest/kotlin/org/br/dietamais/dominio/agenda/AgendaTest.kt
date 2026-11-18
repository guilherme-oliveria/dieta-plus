package org.br.dietamais.dominio.agenda

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.ui.nutri.agenda.agoraFalso
import org.br.dietamais.ui.nutri.agenda.consultaFalsa
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AgendaTest {
    private val fuso = TimeZone.of("America/Sao_Paulo")
    private val paciente = Uuid.random()
    private val quarta = LocalDate(2026, 10, 28)
    private fun hora(h: Int, m: Int = 0) = Instant.parse("2026-10-28T00:00:00Z") + (h + 3).hours + m.minutes // h:m em São Paulo (UTC-3)

    @Test fun semanaComecaNaSegundaETemSeteDias() {
        assertEquals(LocalDate(2026, 10, 26), inicioDaSemana(quarta)); assertEquals(LocalDate(2026, 10, 26), inicioDaSemana(LocalDate(2026, 11, 1))) // domingo fecha a semana anterior
        assertEquals(LocalDate(2026, 11, 2), inicioDaSemana(LocalDate(2026, 11, 2)))
        assertEquals(7, diasDaSemana(quarta).size); assertEquals(LocalDate(2026, 11, 1), diasDaSemana(quarta).last())
    }

    @Test fun diaUsaOFusoDoAparelho() {
        val tardeDaNoite = consultaFalsa(paciente, Instant.parse("2026-10-29T01:30:00Z")) // 22:30 de quarta em São Paulo
        assertEquals(listOf(tardeDaNoite), consultasDoDia(listOf(tardeDaNoite), quarta, fuso))
        assertTrue(consultasDoDia(listOf(tardeDaNoite), LocalDate(2026, 10, 29), fuso).isEmpty())
    }

    @Test fun diaOrdenaPorHorarioESemanaAgrupa() {
        val tarde = consultaFalsa(paciente, hora(15)); val cedo = consultaFalsa(paciente, hora(8)); val quinta = consultaFalsa(paciente, hora(9) + 24.hours)
        assertEquals(listOf(cedo, tarde), consultasDoDia(listOf(tarde, cedo, quinta), quarta, fuso))
        val semana = consultasDaSemana(listOf(tarde, cedo, quinta), quarta, fuso)
        assertEquals(7, semana.size); assertEquals(2, semana[2].second.size); assertEquals(1, semana[3].second.size); assertTrue(semana[0].second.isEmpty())
    }

    @Test fun proximaIgnoraCanceladasEPassadasMasContaAEmAndamento() {
        val passada = consultaFalsa(paciente, agoraFalso - 3.hours); val emAndamento = consultaFalsa(paciente, agoraFalso - 10.minutes)
        val cancelada = consultaFalsa(paciente, agoraFalso + 1.hours, status = StatusConsulta.CANCELADA); val futura = consultaFalsa(paciente, agoraFalso + 5.hours)
        assertEquals(emAndamento, proximaConsulta(listOf(passada, emAndamento, cancelada, futura), agoraFalso))
        assertEquals(futura, proximaConsulta(listOf(passada, cancelada, futura), agoraFalso))
        assertNull(proximaConsulta(listOf(passada, cancelada), agoraFalso))
    }

    @Test fun conflitoSoQuandoSobrepoe() {
        val existente = consultaFalsa(paciente, hora(10), 60)
        assertEquals(1, conflitos(listOf(existente), hora(10, 30), 30).size)
        assertEquals(1, conflitos(listOf(existente), hora(9, 30), 60).size)
        assertTrue(conflitos(listOf(existente), hora(11), 30).isEmpty()) // começa quando a outra termina
        assertTrue(conflitos(listOf(existente), hora(9), 60).isEmpty())
        assertTrue(conflitos(listOf(existente), hora(10), 30, ignorar = existente.id).isEmpty())
        assertTrue(conflitos(listOf(existente.copy(status = StatusConsulta.CANCELADA)), hora(10), 30).isEmpty())
    }

    @Test fun leDataEHora() {
        assertEquals(LocalDate(2026, 10, 28), lerData("28/10/2026")); assertEquals(LocalDate(2026, 1, 5), lerData(" 5/1/2026 "))
        assertNull(lerData("31/02/2026")); assertNull(lerData("28-10-2026")); assertNull(lerData("28/10/26")); assertNull(lerData("a/b/c"))
        assertEquals(9 to 5, lerHora("09:05")); assertEquals(9 to 5, lerHora("9:05")); assertNull(lerHora("24:00")); assertNull(lerHora("09:5")); assertNull(lerHora("0905"))
        assertEquals("28/10/2026", formatarData(quarta)); assertEquals("09:00", formatarHora(agoraFalso, fuso))
    }

    @Test fun formularioValidaCamposEPassado() {
        val ok = FormularioConsulta(pacienteId = paciente, data = "29/10/2026", hora = "14:30", duracao = "50")
        val r = validarFormulario(ok, fuso, agoraFalso)
        assertTrue(r.erros.isEmpty()); assertEquals(Instant.parse("2026-10-29T17:30:00Z"), r.inicio); assertEquals(50, r.duracaoMin)
        assertEquals(setOf(ErroConsulta.PACIENTE, ErroConsulta.DATA, ErroConsulta.HORA, ErroConsulta.DURACAO), validarFormulario(FormularioConsulta(duracao = "4"), fuso, agoraFalso).erros)
        assertEquals(setOf(ErroConsulta.PASSADO), validarFormulario(ok.copy(data = "28/10/2026", hora = "08:00"), fuso, agoraFalso).erros)
        assertTrue(validarFormulario(ok.copy(data = "28/10/2026", hora = "08:57"), fuso, agoraFalso).erros.isEmpty()) // dentro da tolerância de 5 min
        assertEquals(setOf(ErroConsulta.DURACAO), validarFormulario(ok.copy(duracao = "481"), fuso, agoraFalso).erros)
        assertTrue(validarFormulario(ok.copy(pacienteId = null), fuso, agoraFalso, exigePaciente = false).erros.isEmpty())
    }

    @Test fun remarcarPreencheOFormularioNoFusoLocal() {
        val c = consultaFalsa(paciente, Instant.parse("2026-10-29T17:30:00Z"), 45, TipoConsulta.ONLINE, lugar = "https://meet.exemplo.invalid/x", versao = 3)
        val f = formularioDe(c, fuso)
        // C-02 (MOB-84): o tipo legado ONLINE abre como Retorno + Online, com o link antigo que estava em `lugar`
        assertEquals(FormularioConsulta(c.id, 3, paciente, "29/10/2026", "14:30", "45", TipoConsulta.RETORNO, "https://meet.exemplo.invalid/x",
            modalidade = ModalidadeConsulta.ONLINE, link = "https://meet.exemplo.invalid/x"), f)
    }

    @Test fun remarcarTrazModalidadeLinkETextosDaConsulta() {
        val c = consultaFalsa(paciente, Instant.parse("2026-10-29T17:30:00Z"), 50, TipoConsulta.AVALIACAO).copy(modalidade = ModalidadeConsulta.ONLINE,
            linkOnline = "https://meet.exemplo.invalid/y", orientacaoPaciente = "Venha em jejum", observacaoInterna = "Revisar adesão")
        val f = formularioDe(c, fuso)
        assertEquals(TipoConsulta.AVALIACAO, f.tipo); assertEquals(ModalidadeConsulta.ONLINE, f.modalidade); assertEquals("https://meet.exemplo.invalid/y", f.link)
        assertEquals("Venha em jejum", f.orientacao); assertEquals("Revisar adesão", f.observacao)
        assertEquals(ModalidadeConsulta.PRESENCIAL, formularioDe(consultaFalsa(paciente, c.inicio), fuso).modalidade)
    }

    @Test fun linkSoHttpsComHostEAte300Caracteres() {
        assertTrue(linkValido("https://meet.google.com/abc-defg-hij")); assertTrue(linkValido(" https://zoom.us/j/1 "))
        assertFalse(linkValido("http://meet.google.com/abc")); assertFalse(linkValido("meet.google.com/abc")); assertFalse(linkValido("https://"))
        assertFalse(linkValido("https://exemplo.invalid/a b")); assertFalse(linkValido("https://exemplo.invalid/" + "a".repeat(300)))
        val base = FormularioConsulta(pacienteId = paciente, data = "29/10/2026", hora = "14:30")
        assertEquals(setOf(ErroConsulta.LINK), validarFormulario(base.copy(modalidade = ModalidadeConsulta.ONLINE, link = "http://x.invalid"), fuso, agoraFalso).erros)
        assertTrue(validarFormulario(base.copy(modalidade = ModalidadeConsulta.ONLINE), fuso, agoraFalso).erros.isEmpty()) // link é opcional
        assertTrue(validarFormulario(base.copy(link = "http://x.invalid"), fuso, agoraFalso).erros.isEmpty()) // presencial ignora o link
    }

    @Test fun statusRealizadaEFaltaSeguemAsTransicoes() {
        val agendada = consultaFalsa(paciente, agoraFalso)
        assertTrue(podeMarcar(agendada, StatusConsulta.REALIZADA)); assertTrue(podeMarcar(agendada, StatusConsulta.FALTA))
        assertTrue(podeMarcar(agendada.copy(status = StatusConsulta.REALIZADA), StatusConsulta.FALTA))
        assertFalse(podeMarcar(agendada.copy(status = StatusConsulta.REALIZADA), StatusConsulta.REALIZADA))
        assertTrue(podeMarcar(agendada.copy(status = StatusConsulta.FALTA), StatusConsulta.REALIZADA))
        assertFalse(podeMarcar(agendada.copy(status = StatusConsulta.CANCELADA), StatusConsulta.REALIZADA))
        assertFalse(podeMarcar(agendada, StatusConsulta.CANCELADA))
        assertEquals(ComparecimentoConsulta.COMPARECEU, comparecimentoDoStatus(StatusConsulta.REALIZADA))
        assertEquals(ComparecimentoConsulta.FALTOU, comparecimentoDoStatus(StatusConsulta.FALTA)); assertNull(comparecimentoDoStatus(StatusConsulta.AGENDADA))
    }

    // MOB-85: visão do mês
    @Test fun gradeDoMesComecaNoDomingoECompletaASemana() {
        val novembro = gradeDoMes(LocalDate(2026, 11, 19)) // 1/11/2026 é domingo
        assertEquals(LocalDate(2026, 11, 1), novembro.first()); assertEquals(35, novembro.size)
        assertEquals(30, novembro.count { it != null }); assertNull(novembro.last())
        val outubro = gradeDoMes(quarta) // 1/10/2026 é quinta: 4 vazios antes
        assertEquals(List(4) { null }, outubro.take(4)); assertEquals(LocalDate(2026, 10, 1), outubro[4]); assertEquals(0, outubro.size % 7)
        val fevereiro = gradeDoMes(LocalDate(2026, 2, 10)) // 1/2/2026 é domingo, 28 dias: 4 semanas exatas
        assertEquals(28, fevereiro.size); assertTrue(fevereiro.all { it != null })
    }

    @Test fun contagemPorDiaIgnoraCanceladaEOutroMes() {
        val lista = listOf(consultaFalsa(paciente, hora(9)), consultaFalsa(paciente, hora(15)), consultaFalsa(paciente, hora(11), status = StatusConsulta.CANCELADA),
            consultaFalsa(paciente, Instant.parse("2026-11-03T13:00:00Z")))
        assertEquals(mapOf(quarta to 2), consultasPorDiaDoMes(lista, quarta, fuso))
        assertEquals(mapOf(LocalDate(2026, 11, 3) to 1), consultasPorDiaDoMes(lista, LocalDate(2026, 11, 20), fuso))
    }

    @Test fun linhasDoDiaJuntamConsultasEHorariosLivresSemRepetir() {
        val nove = consultaFalsa(paciente, hora(9), 50); val quinze = consultaFalsa(paciente, hora(15), 60)
        val cancelada = consultaFalsa(paciente, hora(12), status = StatusConsulta.CANCELADA)
        val linhas = linhasDoDia(listOf(quinze, nove, cancelada), quarta, fuso, agora = hora(7))
        // 8h livre; 9h ocupada; 10h a 14h livres viram uma linha (a cancelada não ocupa); 15h ocupada; 16h e 17h viram uma
        assertEquals(listOf("L8", "C${nove.id}", "L10", "C${cancelada.id}", "C${quinze.id}", "L16"), linhas.map { if (it is LinhaDoDia.Livre) "L${it.hora}" else "C${(it as LinhaDoDia.Consulta).consulta.id}" })
    }

    @Test fun horarioQueJaPassouNaoEOferecido() {
        val linhas = linhasDoDia(emptyList(), quarta, fuso, agora = hora(13, 30))
        assertEquals(listOf(LinhaDoDia.Livre(14)), linhas)
        assertTrue(linhasDoDia(emptyList(), quarta, fuso, agora = hora(20)).isEmpty())
        // consulta das 9h30 bloqueia 9h e 10h (sobrepõe as duas horas)
        val meia = consultaFalsa(paciente, hora(9, 30), 50)
        assertEquals(listOf(8, 11), linhasDoDia(listOf(meia), quarta, fuso, agora = hora(7)).filterIsInstance<LinhaDoDia.Livre>().map { it.hora })
    }
}
