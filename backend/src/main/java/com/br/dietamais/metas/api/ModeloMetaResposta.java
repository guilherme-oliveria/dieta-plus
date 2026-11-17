package com.br.dietamais.metas.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ModeloMetaResposta(
		@NotNull UUID id,
		@NotBlank String nome,
		@NotNull List<@Valid ItemModeloMeta> itens,
		@NotNull @PositiveOrZero Long versao,
		@NotNull Instant criadoEm,
		@NotNull Instant atualizadoEm) {
}
