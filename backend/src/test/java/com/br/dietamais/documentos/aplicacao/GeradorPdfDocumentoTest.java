package com.br.dietamais.documentos.aplicacao;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.documentos.api.TipoDocumento;
import org.junit.jupiter.api.Test;

/** DES-21 (`PDF-Declaracao`): título, texto, emissor e código de conferência no PDF; HTML do texto é escapado. */
class GeradorPdfDocumentoTest {
	@Test void declaracaoTrazTextoEmissorECodigo() throws Exception {
		var pdf=new GeradorPdfDocumento().gerar(new GeradorPdfDocumento.Conteudo(TipoDocumento.DECLARACAO_COMPARECIMENTO,"Declaração de comparecimento",
			"Declaro que Ana <b>Lima</b> compareceu.\n\nSegundo parágrafo.","Marina Costa","CRN-3 00000","17/11/2026","K7M2-Q9XP-4HTR",null));
		assertThat(new String(pdf,0,5)).isEqualTo("%PDF-");
		String t;
		try(var doc=org.apache.pdfbox.pdmodel.PDDocument.load(pdf)){t=new org.apache.pdfbox.text.PDFTextStripper().getText(doc);}
		assertThat(t).contains("DECLARAÇÃO DE COMPARECIMENTO","Ana <b>Lima</b> compareceu","Segundo parágrafo","Marina Costa","CRN-3 00000","17/11/2026",
			"Código de conferência","K7M2-Q9XP-4HTR","Conferir documento");
	}
	@Test void semCrnNaoImprimeSeparadorVazio() throws Exception {
		var pdf=new GeradorPdfDocumento().gerar(new GeradorPdfDocumento.Conteudo(TipoDocumento.ATESTADO,"Atestado","Atesto.","Marina Costa",null,"17/11/2026","K7M2-Q9XP-4HTR",null));
		String t;
		try(var doc=org.apache.pdfbox.pdmodel.PDDocument.load(pdf)){t=new org.apache.pdfbox.text.PDFTextStripper().getText(doc);}
		assertThat(t).contains("ATESTADO","Nutricionista").doesNotContain("Nutricionista ·");
	}
}
