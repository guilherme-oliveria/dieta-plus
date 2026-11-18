package org.br.dietamais.ui.nutri

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.documentos.FormularioEmissao
import org.br.dietamais.dominio.documentos.FormularioModeloDoc
import org.br.dietamais.dominio.documentos.TrechoPrevia
import org.br.dietamais.ui.nutri.documentos.*
import org.br.dietamais.ui.theme.DietaTheme
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class DocumentosTelasTest {
    private val t = Instant.parse("2026-11-01T00:00:00Z")
    private val paciente = Uuid.random()
    private val valido = DocumentoResposta(Uuid.random(), paciente, TipoDocumento.DECLARACAO_COMPARECIMENTO, "Declaração de comparecimento", "x", "DM7K-42QX",
        Instant.parse("2026-11-17T18:00:00Z"))
    private val anulado = DocumentoResposta(Uuid.random(), paciente, TipoDocumento.ATESTADO, "Atestado", "x", "DM3N-5RJC", Instant.parse("2026-11-03T12:00:00Z"),
        Instant.parse("2026-11-04T12:00:00Z"), "data da consulta errada")
    private val declaracao = ModeloDocumentoResposta(Uuid.random(), TipoDocumento.DECLARACAO_COMPARECIMENTO, "Declaração de comparecimento", "Declaro {{paciente}} {{data}}", true, 1, t, t)
    private val atestado = ModeloDocumentoResposta(Uuid.random(), TipoDocumento.ATESTADO, "Atestado", "Atesto", true, 1, t, t)
    private val meu = ModeloDocumentoResposta(Uuid.random(), TipoDocumento.DECLARACAO_COMPARECIMENTO, "Declaração para academia", "Declaro {{paciente}}", false, 1, t, t)
    private val realizada = ConsultaResposta(Uuid.random(), paciente, Uuid.random(), Instant.parse("2026-11-17T15:00:00Z"), 50, TipoConsulta.RETORNO,
        status = StatusConsulta.REALIZADA, versao = 1, criadoEm = t, atualizadoEm = t)
    private val tamanhos = listOf(Size(360f, 800f), Size(768f, 1024f), Size(1280f, 900f))

    @Test fun listaComValidoEAnuladoNosTresTamanhos() {
        tamanhos.forEach { tamanho ->
            runSkikoComposeUiTest(size = tamanho) {
                var anular: Uuid? = null; var emitir = 0
                setContent { DietaTheme { DocumentosPacienteTela(EstadoDocumentos(paciente, "Ana Lima", carregado = true, documentos = listOf(valido, anulado)),
                    AcoesDocumentos(aoEmitir = { emitir++ }, aoAnular = { anular = it }), fuso = TimeZone.UTC) } }
                onNodeWithText("Emitidos para Ana").assertExists()
                onNodeWithText("Emitida em 17/11/2026 · código DM7K-42QX").assertExists()
                onNodeWithText("Emitido em 03/11/2026 · código DM3N-5RJC").assertExists()
                onNodeWithText("Motivo: data da consulta errada").assertExists()
                onNodeWithText("Válido").assertExists(); onNodeWithText("Anulado").assertExists()
                onAllNodesWithText("Anular").assertCountEquals(1)
                onNodeWithContentDescription("Anular Declaração de comparecimento, código DM7K-42QX").performClick()
                assertEquals(valido.id, anular)
                onNodeWithText("Emitir").performClick(); assertEquals(1, emitir)
            }
        }
    }

    @Test fun semConexaoDesabilitaEmitir() = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        setContent { DietaTheme { DocumentosPacienteTela(EstadoDocumentos(paciente, "Ana", carregado = true, semConexao = true), AcoesDocumentos()) } }
        onNodeWithText("Emitir").assertIsNotEnabled()
        onNodeWithText("Sem conexão. Documentos precisam de internet.").assertExists()
    }

    @Test fun emitirDeclaracaoMostraConsultaEPrevia() = runSkikoComposeUiTest(size = Size(390f, 1400f)) {
        var modelo: Uuid? = null; var confirmar = 0
        val estado = EstadoDocumentos(paciente, "Ana Lima", carregado = true, modelos = listOf(declaracao, atestado, meu), consultas = listOf(realizada),
            emissao = FormularioEmissao(declaracao.id, TipoDocumento.DECLARACAO_COMPARECIMENTO, realizada.id))
        setContent { DietaTheme { EmitirDocumentoConteudo(estado, listOf(TrechoPrevia("Declaro que "), TrechoPrevia("Ana Lima", true)),
            AcoesDocumentos(aoModelo = { modelo = it }, aoConfirmarEmissao = { confirmar++ }), fuso = TimeZone.UTC) } }
        onNodeWithText("Para Ana Lima").assertExists()
        onNodeWithText("17/11/2026 · 15:00 às 15:50 · Realizada").assertExists()
        onNodeWithText("Declaro que Ana Lima").assertExists()
        onNodeWithText("Os campos em verde vêm da ficha e da consulta.").assertExists()
        onNodeWithText("Atestado").performClick(); assertEquals(atestado.id, modelo)
        onNodeWithText("Emitir PDF").performClick(); assertEquals(1, confirmar)
    }

    @Test fun emitirAtestadoPedeDataEHorario() = runSkikoComposeUiTest(size = Size(390f, 1400f)) {
        setContent { DietaTheme { EmitirDocumentoConteudo(EstadoDocumentos(paciente, "Ana", carregado = true, modelos = listOf(atestado),
            emissao = FormularioEmissao(atestado.id, TipoDocumento.ATESTADO), errosEmissao = setOf(org.br.dietamais.dominio.documentos.ErroEmissao.DATA)), null, AcoesDocumentos()) } }
        onNodeWithTag("documento-data").assertExists(); onNodeWithTag("documento-horario").assertExists()
        onNodeWithText("Data inválida. Use dd/mm/aaaa.").assertExists()
    }

    @Test fun anularPedeMotivo() = runSkikoComposeUiTest(size = Size(390f, 900f)) {
        var confirmar = 0
        setContent { DietaTheme { AnularDocumentoConteudo(EstadoDocumentos(paciente, "Ana", carregado = true, documentos = listOf(valido), anulando = valido.id, erroMotivo = true),
            AcoesDocumentos(aoConfirmarAnulacao = { confirmar++ }), fuso = TimeZone.UTC) } }
        onNodeWithText("Anular declaração de comparecimento?").assertExists()
        onNodeWithText("Escreva o motivo (até 300 caracteres).").assertExists()
        onNodeWithText("Anular documento").performClick(); assertEquals(1, confirmar)
    }

    @Test fun modelosDoSistemaCopiamEOsMeusEditam() = runSkikoComposeUiTest(size = Size(390f, 1200f)) {
        var aberto: Uuid? = null; var excluir: Uuid? = null
        setContent { DietaTheme { ModelosDocumentoCard(EstadoModelosDocumento(modelos = listOf(declaracao, meu)),
            AcoesModelosDocumento(aoAbrir = { aberto = it }, aoExcluir = { excluir = it })) } }
        onNodeWithText("Do Dieta+, só leitura").assertExists()
        onNodeWithText("Campos: paciente, data").assertExists()
        onNodeWithText("Copiar para editar").performClick(); assertEquals(declaracao.id, aberto)
        onNodeWithContentDescription("Editar o modelo Declaração para academia").performClick(); assertEquals(meu.id, aberto)
        onNodeWithContentDescription("Excluir o modelo Declaração para academia").performClick(); assertEquals(meu.id, excluir)
    }

    @Test fun formularioDoModeloInsereCampo() = runSkikoComposeUiTest(size = Size(390f, 1200f)) {
        var campo: String? = null
        setContent { DietaTheme { Column { FormularioModeloDocConteudo(EstadoModelosDocumento(formulario = FormularioModeloDoc(nome = "Academia")),
            AcoesModelosDocumento(aoCampo = { campo = it })) } } }
        onNodeWithText("Novo modelo de documento").assertExists()
        onNodeWithText("CRN").performClick(); assertEquals("crn", campo)
    }
}
