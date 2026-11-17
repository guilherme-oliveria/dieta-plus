package com.br.dietamais.prescricoes.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record ModeloOrientacaoResposta(
		@NotNull UUID id,
		@NotBlank String titulo,
		@NotBlank String texto,
		@NotNull @PositiveOrZero Long versao,
		@NotNull Instant criadoEm,
		@NotNull Instant atualizadoEm) {
}
