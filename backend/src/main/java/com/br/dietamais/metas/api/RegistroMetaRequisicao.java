package com.br.dietamais.metas.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** `POST /eu/registros-meta` e coleção `registrosMeta` do sync (só `CRIAR`, sem conflito; RN-MET-02). SIM_NAO usa 1 ou 0. */
public record RegistroMetaRequisicao(
		UUID id,
		@NotNull UUID metaId,
		@NotNull LocalDate data,
		@NotNull @PositiveOrZero BigDecimal valor,
		@NotNull Instant registradoEm) {
}
