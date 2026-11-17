package com.br.dietamais.documentos.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

/** Documento emitido (visão da nutri). `codigoConferencia` é impresso no PDF (RN-DOC-03). */
public record DocumentoResposta(
		@NotNull UUID id,
		@NotNull UUID pacienteId,
		@NotNull TipoDocumento tipo,
		@NotBlank String titulo,
		@NotBlank String texto,
		@NotBlank String codigoConferencia,
		@NotNull Instant emitidoEm,
		@Schema(nullable=true) Instant anuladoEm,
		@Schema(nullable=true) String motivoAnulacao) {
}
