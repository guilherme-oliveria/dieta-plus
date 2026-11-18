package com.br.dietamais.documentos.aplicacao;

import com.br.dietamais.documentos.api.TipoDocumento;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * PDF A4 do atestado e da declaração (DES-21, `PDF-Declaracao`): logo, nome e CRN da nutri, título, texto final, bloco do emissor
 * e o código de conferência no rodapé. Sem assinatura digitalizada na 1.4.0 (D-05). Recebe tudo pronto, sem banco.
 */
class GeradorPdfDocumento {
	record Logo(byte[] conteudo,String tipo) { }
	record Conteudo(TipoDocumento tipo,String titulo,String texto,String nutri,String crn,String dataLocal,String codigo,Logo logo) { }

	byte[] gerar(Conteudo c){
		var h=new StringBuilder();
		h.append("<div class=\"rodape\">Código de conferência: <strong>").append(esc(c.codigo())).append("</strong><br/>Confira no site do Dieta+, em “Conferir documento”<br/>Emitido pelo Dieta+</div>");
		h.append("<div class=\"topo\">");
		if(c.logo()!=null)h.append("<img class=\"logo\" src=\"data:").append(c.logo().tipo()).append(";base64,").append(Base64.getEncoder().encodeToString(c.logo().conteudo())).append("\" />");
		h.append("<div class=\"quem\"><strong>").append(esc(c.nutri())).append("</strong><br/>Nutricionista").append(c.crn()==null||c.crn().isBlank()?"":" · "+esc(c.crn())).append("</div>");
		h.append("<div class=\"data\">").append(esc(c.dataLocal())).append("</div></div>");
		h.append("<h1>").append(esc(c.titulo().toUpperCase(java.util.Locale.of("pt","BR")))).append("</h1>");
		for(String p:c.texto().split("\n\\s*\n"))h.append("<p class=\"texto\">").append(esc(p.strip())).append("</p>");
		h.append("<div class=\"emissor\"><div class=\"linha\"></div><strong>").append(esc(c.nutri())).append("</strong><br/>Nutricionista")
			.append(c.crn()==null||c.crn().isBlank()?"":" · "+esc(c.crn())).append("</div>");
		return pdf(h.toString());
	}

	static String esc(String s){
		if(s==null)return "";
		var o=new StringBuilder(s.length()+8);
		for(char ch:s.toCharArray())switch(ch){case '&'->o.append("&amp;");case '<'->o.append("&lt;");case '>'->o.append("&gt;");case '"'->o.append("&quot;");case '\''->o.append("&#39;");
			default->{if(ch>=0x20||ch=='\n'||ch=='\t')o.append(ch);}}
		return o.toString().replace("\n","<br/>");
	}

	private static final String CSS="""
		@page { size: A4; margin: 24mm 22mm 40mm 22mm; @bottom-center { content: element(rodape); } }
		body { font-family: 'Plus Jakarta Sans', sans-serif; font-size: 12pt; color: #1b1f1c; }
		.rodape { position: running(rodape); text-align: center; font-size: 9pt; color: #555; border-top: 1px solid #d6dad3; padding-top: 6px; }
		.topo { border-bottom: 2px solid #2E7D4F; padding-bottom: 10px; margin-bottom: 36px; overflow: hidden; }
		.logo { max-height: 56px; max-width: 160px; float: left; margin-right: 14px; }
		.quem { float: left; font-size: 11pt; padding-top: 6px; }
		.data { float: right; font-size: 10.5pt; color: #555; padding-top: 14px; }
		h1 { font-size: 16pt; color: #2E7D4F; text-align: center; letter-spacing: 1px; margin: 0 0 28px 0; }
		.texto { line-height: 1.7; text-align: justify; margin: 0 0 14px 0; }
		.emissor { margin: 72px auto 0 auto; width: 60%; text-align: center; font-size: 11pt; page-break-inside: avoid; }
		.linha { border-top: 1px solid #1b1f1c; margin-bottom: 6px; }
		""";

	private byte[] pdf(String corpo){
		String html="<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/><style>"+CSS+"</style></head><body>"+corpo+"</body></html>";
		try(var saida=new ByteArrayOutputStream()){
			var b=new PdfRendererBuilder();
			b.useFont(()->GeradorPdfDocumento.class.getResourceAsStream("/fontes/plus_jakarta_sans_regular.ttf"),"Plus Jakarta Sans",400,com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL,true);
			b.useFont(()->GeradorPdfDocumento.class.getResourceAsStream("/fontes/plus_jakarta_sans_bold.ttf"),"Plus Jakarta Sans",700,com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL,true);
			b.useFastMode();b.withHtmlContent(html,null);b.toStream(saida);b.run();
			return saida.toByteArray();
		}catch(Exception e){throw new IllegalStateException("Falha ao gerar o PDF do documento",e);}
	}
}
