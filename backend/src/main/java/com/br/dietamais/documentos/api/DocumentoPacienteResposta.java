package com.br.dietamais.documentos.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

/** Metadados do documento para o paciente (`GET /eu/documentos` e coleção `documentos` do sync): sem texto e sem motivo de anulação. */
public record DocumentoPacienteResposta(
		@NotNull UUID id,
		@NotNull TipoDocumento tipo,
		@NotBlank String titulo,
		@NotNull Instant emitidoEm,
		@NotNull Boolean anulado,
		@NotNull @PositiveOrZero Long versao) {
}
