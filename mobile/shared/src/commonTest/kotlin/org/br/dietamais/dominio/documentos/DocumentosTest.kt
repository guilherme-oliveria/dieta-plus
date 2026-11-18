package org.br.dietamais.dominio.documentos

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.*
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DocumentosTest {
    private val t = Instant.parse("2026-11-01T00:00:00Z")
    private val paciente = Uuid.random()
    private fun consulta(status: StatusConsulta, inicio: String, p: Uuid = paciente) = ConsultaResposta(Uuid.random(), p, Uuid.random(), Instant.parse(inicio), 50,
        TipoConsulta.entries.first(), status = status, versao = 1, criadoEm = t, atualizadoEm = t)
    private fun modelo(tipo: TipoDocumento, nome: String, sistema: Boolean) = ModeloDocumentoResposta(Uuid.random(), tipo, nome, "Texto {{paciente}}", sistema, 1, t, t)

    @Test fun previaTrocaCamposEMarcaOQueVeioDaFicha() {
        val p = previaDocumento("Declaro que {{paciente}} compareceu em {{data}}, das {{horario}}. {{ nutri }} · {{crn}} {{outro}}",
            CamposPrevia("Ana Lima", "17/11/2026", "15:00 às 15:50", "Nut. Marina", ""))
        assertEquals("Declaro que Ana Lima compareceu em 17/11/2026, das 15:00 às 15:50. Nut. Marina · ___ {{outro}}", p.joinToString("") { it.texto })
        assertEquals(listOf("Ana Lima", "17/11/2026", "15:00 às 15:50", "Nut. Marina", "___"), p.filter { it.campo }.map { it.texto })
    }

    @Test fun camposDoTextoNaOrdemPadrao() {
        assertEquals(listOf("paciente", "data", "crn"), camposDoTexto("{{crn}} {{data}} {{paciente}}"))
    }

    @Test fun modelosDoSistemaPrimeiro() {
        val meu = modelo(TipoDocumento.DECLARACAO_COMPARECIMENTO, "Academia", false)
        val atestado = modelo(TipoDocumento.ATESTADO, "Atestado", true)
        val declaracao = modelo(TipoDocumento.DECLARACAO_COMPARECIMENTO, "Declaração de comparecimento", true)
        assertEquals(listOf(atestado, declaracao, meu), modelosOrdenados(listOf(meu, atestado, declaracao)))
    }

    @Test fun soConsultasRealizadasDoPacienteDaMaisRecente() {
        val antiga = consulta(StatusConsulta.REALIZADA, "2026-10-01T15:00:00Z")
        val nova = consulta(StatusConsulta.REALIZADA, "2026-11-17T15:00:00Z")
        val agendada = consulta(StatusConsulta.AGENDADA, "2026-11-20T15:00:00Z")
        val outra = consulta(StatusConsulta.REALIZADA, "2026-11-18T15:00:00Z", Uuid.random())
        assertEquals(listOf(nova, antiga), consultasParaDeclaracao(listOf(antiga, agendada, nova, outra), paciente))
    }

    @Test fun validaEmissaoPorTipo() {
        val m = Uuid.random()
        assertEquals(setOf(ErroEmissao.MODELO), validarEmissao(FormularioEmissao()))
        assertEquals(setOf(ErroEmissao.CONSULTA), validarEmissao(FormularioEmissao(m, TipoDocumento.DECLARACAO_COMPARECIMENTO)))
        assertEquals(setOf(ErroEmissao.DATA, ErroEmissao.HORARIO), validarEmissao(FormularioEmissao(m, TipoDocumento.ATESTADO, data = "31/02", horario = "25:00")))
        assertTrue(validarEmissao(FormularioEmissao(m, TipoDocumento.ATESTADO)).isEmpty())
    }

    @Test fun requisicaoDaDeclaracaoLevaSoAConsultaEADoAtestadoDataEHora() {
        val m = Uuid.random(); val c = Uuid.random(); val id = Uuid.random()
        assertEquals(EmitirDocumentoRequisicao(id, m, consultaId = c),
            requisicaoEmissao(FormularioEmissao(m, TipoDocumento.DECLARACAO_COMPARECIMENTO, c, "17/11/2026", "10:00"), id))
        assertEquals(EmitirDocumentoRequisicao(id, m, data = LocalDate(2026, 11, 17), horario = "08:05"),
            requisicaoEmissao(FormularioEmissao(m, TipoDocumento.ATESTADO, data = "17/11/2026", horario = "8:05"), id))
        assertEquals(EmitirDocumentoRequisicao(id, m), requisicaoEmissao(FormularioEmissao(m, TipoDocumento.ATESTADO), id))
    }

    @Test fun camposDaPreviaComConsultaOuDataDigitada() {
        val c = consulta(StatusConsulta.REALIZADA, "2026-11-17T15:00:00Z")
        val hoje = LocalDate(2026, 11, 18)
        assertEquals(CamposPrevia("Ana", "17/11/2026", "15:00 às 15:50", "Nut", "CRN"),
            camposPrevia(FormularioEmissao(), "Ana", "Nut", "CRN", c, hoje, TimeZone.UTC))
        assertEquals("18/11/2026", camposPrevia(FormularioEmissao(), "Ana", "Nut", "CRN", null, hoje, TimeZone.UTC).data)
        assertEquals("", camposPrevia(FormularioEmissao(data = "xx"), "Ana", "Nut", "CRN", null, hoje, TimeZone.UTC).data)
    }

    @Test fun copiarModeloDoSistemaCriaNovoEEditarOMeuMantemVersao() {
        val sistema = modelo(TipoDocumento.ATESTADO, "Atestado", true)
        val copia = formularioDeModelo(sistema, "(cópia)")
        assertFalse(copia.edicao); assertEquals("Atestado (cópia)", copia.nome); assertEquals(sistema.texto, copia.texto)
        val meu = modelo(TipoDocumento.ATESTADO, "Meu", false)
        val edicao = formularioDeModelo(meu, "(cópia)")
        assertTrue(edicao.edicao); assertEquals(meu.id, edicao.modeloId); assertEquals(1L, edicao.versao)
    }

    @Test fun validaModeloEMotivo() {
        assertEquals(setOf(ErroModeloDoc.NOME, ErroModeloDoc.TEXTO), validarModeloDoc(FormularioModeloDoc(nome = " ", texto = "")))
        assertEquals(setOf(ErroModeloDoc.NOME), validarModeloDoc(FormularioModeloDoc(nome = "x".repeat(81), texto = "ok")))
        assertTrue(validarModeloDoc(FormularioModeloDoc(nome = "Academia", texto = "ok")).isEmpty())
        assertFalse(motivoValido("  ")); assertFalse(motivoValido("x".repeat(301))); assertTrue(motivoValido("Data errada"))
    }
}
