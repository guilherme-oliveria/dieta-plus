package com.br.dietamais.assinaturas.api;


/**
 * URL do checkout hospedado do gateway; o app abre no navegador externo e nunca vende dentro dele.
 * BE-81b (RN-ASS-10): na troca para plano menor com a assinatura ativa não há cobrança agora; `url` vem nula e `trocaAgendadaPara` diz o dia em que o plano muda.
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record CheckoutResposta(@io.swagger.v3.oas.annotations.media.Schema(nullable=true) String url,
		@io.swagger.v3.oas.annotations.media.Schema(nullable=true) java.time.LocalDate trocaAgendadaPara) {
	public CheckoutResposta(String url){this(url,null);}
}
