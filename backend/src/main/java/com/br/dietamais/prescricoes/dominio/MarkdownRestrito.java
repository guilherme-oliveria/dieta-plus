package com.br.dietamais.prescricoes.dominio;

import java.util.regex.Pattern;

/**
 * RN-ORI-01: Markdown restrito (negrito, itálico e listas). HTML é descartado antes de guardar; imagens e links viram só o texto,
 * para o app nunca receber um endereço executável.
 */
public final class MarkdownRestrito {
	private static final Pattern COMENTARIO=Pattern.compile("<!--.*?-->",Pattern.DOTALL);
	private static final Pattern BLOCO=Pattern.compile("<(script|style)\\b.*?</\\1\\s*>",Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
	private static final Pattern TAG=Pattern.compile("</?[A-Za-z!?][^>]*>");
	private static final Pattern IMAGEM=Pattern.compile("!\\[([^\\]]*)\\]\\([^)]*\\)");
	private static final Pattern LINK=Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
	private MarkdownRestrito(){}

	public static String limpar(String texto){
		if(texto==null)return "";
		String t=texto.replace("\r\n","\n").replace('\r','\n');
		String anterior;
		do{anterior=t;t=COMENTARIO.matcher(t).replaceAll("");t=BLOCO.matcher(t).replaceAll("");t=TAG.matcher(t).replaceAll("");}while(!t.equals(anterior));
		t=IMAGEM.matcher(t).replaceAll("$1");t=LINK.matcher(t).replaceAll("$1");
		t=t.replace("<","").replace(">",""); // sobra de tag mal formada
		return t.strip();
	}
}
