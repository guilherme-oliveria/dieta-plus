package com.br.dietamais.metas.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Meta de hábito. `alvoPadrao` é true quando o alvo da água ainda é o padrão de 35 ml por kg (RN-MET-05). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetaResposta(
		@NotNull UUID id,
		@NotNull UUID pacienteId,
		@NotNull TipoMeta tipo,
		@Schema(nullable=true) String nome,
		@NotNull @Positive BigDecimal alvo,
		@Schema(nullable=true) String unidade,
		@NotNull FrequenciaMeta frequencia,
		@Schema(nullable=true) Integer vezesPorSemana,
		@Schema(nullable=true) LocalDate inicio,
		@Schema(nullable=true) LocalDate fim,
		@Schema(nullable=true) Boolean alvoPadrao,
		@NotNull @PositiveOrZero Long versao,
		@NotNull Instant criadoEm,
		@NotNull Instant atualizadoEm) {
}
