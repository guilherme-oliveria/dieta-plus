package com.br.dietamais.prescricoes.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record OrientacaoResposta(
		@NotNull UUID id,
		@NotNull UUID pacienteId,
		@NotBlank String titulo,
		@NotBlank String texto,
		@Schema(nullable=true) Instant arquivadaEm,
		@NotNull @PositiveOrZero Long versao,
		@NotNull Instant criadoEm,
		@NotNull Instant atualizadoEm) {
}
