package com.br.dietamais.rotina.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** `POST /eu/registros-fora-do-plano` e coleção `registrosForaDoPlano` do sync (só `CRIAR`, sem conflito; RN-ROT-06, BE-C9, H-03). */
public record RegistroForaDoPlanoRequisicao(
		UUID id,
		@NotNull LocalDate data,
		@NotBlank @Size(max=280) String texto,
		UUID refeicaoId,
		@NotNull Instant registradoEm) {
}
