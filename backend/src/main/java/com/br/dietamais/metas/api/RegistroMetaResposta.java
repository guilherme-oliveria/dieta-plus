package com.br.dietamais.metas.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RegistroMetaResposta(
		@NotNull UUID id,
		@NotNull UUID metaId,
		@NotNull LocalDate data,
		@NotNull @PositiveOrZero BigDecimal valor,
		@NotNull Instant registradoEm) {
}
