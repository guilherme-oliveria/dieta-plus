package com.br.dietamais.assinaturas.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Corpo de `POST /assinatura/checkout`. O app 1.4.0 manda `planoCodigo` e `periodicidade` (BE-C10, {@link CheckoutPlanoRequisicao});
 * o corpo antigo `{ "plano": "..." }` do app 1.3.x segue aceito por pelo menos 6 meses. Sem periodicidade, vale MENSAL.
 */
public record CheckoutRequisicao(
		@Schema(nullable=true,description="Legado (app 1.3.x): o mesmo que planoCodigo") @Size(max=40) String plano,
		@Schema(nullable=true) @Size(max=40) String planoCodigo,
		@Schema(nullable=true) Periodicidade periodicidade) {
	public CheckoutRequisicao(String plano){this(plano,null,null);}
	/** O código do plano, venha pelo campo novo ou pelo antigo. */
	public String codigo(){return planoCodigo!=null&&!planoCodigo.isBlank()?planoCodigo.strip():plano==null||plano.isBlank()?null:plano.strip();}
	public Periodicidade periodicidadeOuMensal(){return periodicidade==null?Periodicidade.MENSAL:periodicidade;}
}
