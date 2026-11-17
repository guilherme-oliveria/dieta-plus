package com.br.dietamais.metas.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** `PATCH /metas/{id}` com `If-Match`. Campos ausentes não mudam; `encerrar=true` fecha a meta (a meta de água não se encerra, só se ajusta). */
public record AtualizarMetaRequisicao(
		@Size(max=80) String nome,
		@Positive BigDecimal alvo,
		@Size(max=20) String unidade,
		FrequenciaMeta frequencia,
		@Min(1) @Max(7) Integer vezesPorSemana,
		LocalDate inicio,
		LocalDate fim,
		Boolean encerrar) {
}
