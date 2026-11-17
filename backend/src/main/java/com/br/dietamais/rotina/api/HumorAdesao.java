package com.br.dietamais.rotina.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** `humor` de `GET /pacientes/{id}/adesao` (opcional, RN-ROT-01): médias de 1 a 5 da semana; nulas sem nenhum registro. */
public record HumorAdesao(
		@Schema(nullable=true) BigDecimal mediaFomeAntes,
		@Schema(nullable=true) BigDecimal mediaSensacaoDepois,
		@NotNull @PositiveOrZero Integer registros) {
}
