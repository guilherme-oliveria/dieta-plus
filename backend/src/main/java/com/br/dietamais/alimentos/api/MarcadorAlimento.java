package com.br.dietamais.alimentos.api;

/**
 * Restrição que o alimento carrega (BE-51). Filtra a lista do paciente e vira restrição do paciente quando um modelo de dieta é aplicado
 * (RN-MOD-03). A sugestão inicial do catálogo é [SUPOSIÇÃO G-15]; a nutri ajusta nos alimentos dela.
 */
public enum MarcadorAlimento {
	CONTEM_LACTOSE,CONTEM_GLUTEN,ALTO_SODIO,ACUCAR_ADICIONADO,ALTO_POTASSIO,
	/** BE-69 (H-01): marcadores de alergia e intolerância; sugestão inicial por nome do catálogo, [SUPOSIÇÃO D-10]. */
	AMENDOIM,FRUTOS_DO_MAR,OVO,SOJA
}
