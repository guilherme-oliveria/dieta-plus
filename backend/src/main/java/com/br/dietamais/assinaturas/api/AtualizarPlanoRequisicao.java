package com.br.dietamais.assinaturas.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** `limitePacientes` nulo = sem limite; `precoAnual` nulo = sem venda anual; `aVenda` ausente mantém o valor (BE-80, RN-ASS-01 e 09). O código do plano não muda. Preço novo vale para novos checkouts; quem já assina continua no valor do gateway. */
public record AtualizarPlanoRequisicao(
	@NotBlank @Size(max=100) String nome,
	@NotNull @PositiveOrZero @Digits(integer=8,fraction=2) BigDecimal precoMensal,
	@Min(1) @Max(100000) Integer limitePacientes,
	@PositiveOrZero @Digits(integer=8,fraction=2) BigDecimal precoAnual,
	Boolean aVenda,
	@Min(1) @Max(3650) Integer duracaoDias,
	@NotNull Boolean ativo,
	@NotBlank @Size(max=500) String motivo) { }
