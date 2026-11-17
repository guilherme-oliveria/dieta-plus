package com.br.dietamais.assinaturas.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Plano à venda em `GET /api/v1/planos` (público, BE-C10, H-07). `limitePacientes` nulo = sem limite; `precoAnual` nulo = sem venda anual (RN-ASS-09). */
public record PlanoPublicoResposta(
		@NotBlank String codigo,
		@NotBlank String nome,
		@NotNull @PositiveOrZero BigDecimal precoMensal,
		@Schema(nullable=true) BigDecimal precoAnual,
		@Schema(nullable=true) Integer limitePacientes) {
}
