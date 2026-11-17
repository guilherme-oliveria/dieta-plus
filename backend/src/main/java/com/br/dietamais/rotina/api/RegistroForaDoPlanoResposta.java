package com.br.dietamais.rotina.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RegistroForaDoPlanoResposta(
		@NotNull UUID id,
		@NotNull LocalDate data,
		@NotBlank String texto,
		@Schema(nullable=true) UUID refeicaoId,
		@NotNull Instant registradoEm) {
}
