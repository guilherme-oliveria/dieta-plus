package com.br.dietamais.assinaturas.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** `limitePacientes` nulo = sem limite; `aVenda` ausente = à venda quando o preço é maior que zero (BE-80). */
public record CriarPlanoRequisicao(
	@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,39}") String codigo,
	@NotBlank @Size(max=100) String nome,
	@NotNull @PositiveOrZero @Digits(integer=8,fraction=2) BigDecimal precoMensal,
	@Min(1) @Max(100000) Integer limitePacientes,
	@PositiveOrZero @Digits(integer=8,fraction=2) BigDecimal precoAnual,
	Boolean aVenda,
	@Min(1) @Max(3650) Integer duracaoDias,
	@NotBlank @Size(max=500) String motivo) { }
