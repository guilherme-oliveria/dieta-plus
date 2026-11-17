package com.br.dietamais.metas.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** `POST /pacientes/{id}/metas` (RN-MET-01, BE-C9, H-04). `nome` e `unidade` só em SIM_NAO e CONTADOR. `vezesPorSemana` só com frequência SEMANAL. */
public record MetaRequisicao(
		UUID id,
		@NotNull TipoMeta tipo,
		@Size(max=80) String nome,
		@NotNull @Positive BigDecimal alvo,
		@Size(max=20) String unidade,
		@NotNull FrequenciaMeta frequencia,
		@Min(1) @Max(7) Integer vezesPorSemana,
		LocalDate inicio,
		LocalDate fim) {
}
