package com.br.dietamais.assinaturas.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * Campos novos opcionais de `GET /assinatura` (BE-C10, RN-ASS-10 e 11), somados aos de `AssinaturaDetalheResposta`.
 * `exibePrecoEBotao`: chave por plataforma (D-09): false em lojas que exigem compra no aplicativo; o app esconde preço e botão de assinar.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssinaturaAmpliadaResposta(
		@Schema(nullable=true) Periodicidade periodicidade,
		@Schema(nullable=true) @PositiveOrZero Integer diasRestantesTeste,
		@Schema(nullable=true) Boolean exibePrecoEBotao,
		@Schema(nullable=true) java.util.List<@jakarta.validation.Valid PlanoPublicoResposta> planosDisponiveis) {
}
