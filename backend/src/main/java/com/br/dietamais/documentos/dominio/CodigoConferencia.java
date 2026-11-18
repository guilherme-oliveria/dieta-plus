package com.br.dietamais.documentos.dominio;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * RN-DOC-03: código de conferência aleatório, impresso no documento, no formato {@code XXXX-XXXX-XXXX} (cerca de 59 bits).
 * O alfabeto não tem 0, O, 1, I nem L, que se confundem ao digitar.
 */
public final class CodigoConferencia {
	static final String ALFABETO="23456789ABCDEFGHJKMNPQRSTUVWXYZ";
	private static final java.util.regex.Pattern FORMATO=java.util.regex.Pattern.compile("^["+ALFABETO+"]{4}-["+ALFABETO+"]{4}-["+ALFABETO+"]{4}$");
	private final RandomGenerator aleatorio;
	public CodigoConferencia(){this(new SecureRandom());}
	public CodigoConferencia(RandomGenerator aleatorio){this.aleatorio=aleatorio;}

	public String novo(){
		var s=new StringBuilder(14);
		for(int i=0;i<12;i++){if(i>0&&i%4==0)s.append('-');s.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));}
		return s.toString();
	}
	/** Normaliza o que a pessoa digitou (minúsculas, espaços, sem hífen); nulo se não tem o formato. */
	public static String normalizar(String digitado){
		if(digitado==null)return null;
		String s=digitado.toUpperCase(java.util.Locale.ROOT).replaceAll("[^0-9A-Z]","");
		if(s.length()!=12)return null;
		String c=s.substring(0,4)+"-"+s.substring(4,8)+"-"+s.substring(8);
		return FORMATO.matcher(c).matches()?c:null;
	}
}
