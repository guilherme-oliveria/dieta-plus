package com.br.dietamais.documentos.dominio;

import java.util.*;
import java.util.regex.Pattern;

/**
 * RN-DOC-01: troca os campos {@code {{paciente}}}, {@code {{data}}}, {@code {{horario}}}, {@code {{nutri}}} e {@code {{crn}}} pelos valores.
 * Campo desconhecido, ou conhecido sem valor, fica em {@link Resultado#faltando()}: o documento não é emitido pela metade.
 */
public final class PreenchimentoDocumento {
	public static final Set<String> CAMPOS=Set.of("paciente","data","horario","nutri","crn");
	private static final Pattern CAMPO=Pattern.compile("\\{\\{\\s*([A-Za-z]+)\\s*}}");
	private PreenchimentoDocumento(){}

	public record Resultado(String texto,List<String> faltando) {
		public boolean completo(){return faltando.isEmpty();}
	}

	public static Resultado preencher(String modelo,Map<String,String> valores){
		var faltando=new LinkedHashSet<String>();
		var m=CAMPO.matcher(modelo);var saida=new StringBuilder();
		while(m.find()){
			String nome=m.group(1).toLowerCase(Locale.ROOT);String valor=CAMPOS.contains(nome)?valores.get(nome):null;
			if(valor==null||valor.isBlank()){faltando.add(m.group(1));m.appendReplacement(saida,java.util.regex.Matcher.quoteReplacement(m.group()));}
			else m.appendReplacement(saida,java.util.regex.Matcher.quoteReplacement(valor.strip()));
		}
		m.appendTail(saida);
		return new Resultado(limpar(saida.toString()),List.copyOf(faltando));
	}

	/** Texto simples: sem caracteres de controle (menos quebra de linha e tab), quebras normalizadas e sem espaço nas pontas. */
	public static String limpar(String t){
		if(t==null)return "";
		var o=new StringBuilder(t.length());
		for(char c:t.replace("\r\n","\n").replace('\r','\n').toCharArray())if(c>=0x20||c=='\n'||c=='\t')o.append(c);
		return o.toString().strip();
	}
}
