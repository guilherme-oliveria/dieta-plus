package com.br.dietamais.assinaturas.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlanoAdminResposta(
	@NotNull UUID id,
	@NotBlank String codigo,
	@NotBlank String nome,
	@NotNull @PositiveOrZero BigDecimal precoMensal,
	@io.swagger.v3.oas.annotations.media.Schema(nullable=true) @Positive Integer limitePacientes,
	@io.swagger.v3.oas.annotations.media.Schema(nullable=true) BigDecimal precoAnual,
	@NotNull Boolean aVenda,
	Integer duracaoDias,
	@NotNull Boolean ativo,
	@NotNull Instant criadoEm) { }
