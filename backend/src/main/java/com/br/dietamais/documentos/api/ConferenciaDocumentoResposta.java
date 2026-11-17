package com.br.dietamais.documentos.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;

/** `GET /api/v1/documentos/conferir/{codigo}` (pública, RN-DOC-03): nenhum dado do paciente, nem iniciais (C-06, D-04). */
public record ConferenciaDocumentoResposta(
		@NotNull TipoDocumento tipo,
		@NotBlank String emissor,
		@Schema(nullable=true) String crn,
		@NotNull Instant emitidoEm,
		@NotNull SituacaoDocumento situacao) {
}
