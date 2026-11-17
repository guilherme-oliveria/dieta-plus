package com.br.dietamais.metas.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Item de um modelo de metas (RN-MET-04). A água não entra em modelo com alvo fixo: o alvo é por paciente (RN-MET-05). */
public record ItemModeloMeta(
		@NotNull TipoMeta tipo,
		@Size(max=80) String nome,
		@NotNull @Positive BigDecimal alvo,
		@Size(max=20) String unidade,
		@NotNull FrequenciaMeta frequencia,
		@Min(1) @Max(7) Integer vezesPorSemana) {
}
