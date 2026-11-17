package com.br.dietamais.documentos.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record ModeloDocumentoResposta(
		@NotNull UUID id,
		@NotNull TipoDocumento tipo,
		@NotBlank String nome,
		@NotBlank String texto,
		@NotNull Boolean doSistema,
		@NotNull @PositiveOrZero Long versao,
		@NotNull Instant criadoEm,
		@NotNull Instant atualizadoEm) {
}
