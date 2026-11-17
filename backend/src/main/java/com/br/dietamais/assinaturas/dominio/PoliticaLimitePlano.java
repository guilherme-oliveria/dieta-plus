package com.br.dietamais.assinaturas.dominio;

import com.br.dietamais.comum.api.*;

public final class PoliticaLimitePlano {
	private PoliticaLimitePlano(){}
	/** Limite nulo = sem limite (RN-ASS-01). */
	public static void validar(int ocupados,Integer limite) {
		if(limite!=null&&ocupados>=limite)throw new ExcecaoNegocio(CodigoErro.LIMITE_PACIENTES_ATINGIDO,"RN-ASS-03");
	}
}
