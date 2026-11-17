package com.br.dietamais.assinaturas.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PlanoResposta(@NotBlank String codigo,@NotBlank String nome,@NotNull @PositiveOrZero BigDecimal precoMensal,@io.swagger.v3.oas.annotations.media.Schema(nullable=true) @PositiveOrZero Integer limitePacientes) { }
