package com.br.dietamais.prescricoes.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Prescrição de suplemento. O paciente a recebe sem `produtoId` (não vê o catálogo da nutri, C-08). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuplementoResposta(
		@NotNull UUID id,
		@NotNull UUID pacienteId,
		@Schema(nullable=true) UUID produtoId,
		@NotBlank String nome,
		@NotBlank String dose,
		@Schema(nullable=true) String forma,
		@Schema(nullable=true) String frequencia,
		@NotNull List<String> horarios,
		@NotNull LocalDate inicio,
		@Schema(nullable=true) LocalDate fim,
		@NotNull @PositiveOrZero Long versao,
		@NotNull Instant criadoEm,
		@NotNull Instant atualizadoEm) {
}
