package com.br.dietamais.prescricoes.api;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/** `PATCH /suplementos/{id}` com `If-Match` (RN-SUP-02). `encerrar=true` fecha hoje; nome e produto não mudam (cópia congelada). */
public record AtualizarSuplementoRequisicao(
		@Size(max=80) String dose,
		@Size(max=60) String forma,
		@Size(max=120) String frequencia,
		@Size(max=6) List<@Pattern(regexp="^([01]\\d|2[0-3]):[0-5]\\d$") String> horarios,
		LocalDate fim,
		Boolean encerrar) {
}
