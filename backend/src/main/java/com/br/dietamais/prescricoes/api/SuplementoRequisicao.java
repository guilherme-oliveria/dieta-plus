package com.br.dietamais.prescricoes.api;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * `POST /pacientes/{id}/suplementos` (RN-SUP-01, BE-C9, H-05). Ou `produtoId` (produto da nutri, copiado) ou `nome` (texto livre).
 * O servidor guarda cópia congelada de nome e dose. `horarios` em HH:mm, até 6.
 */
public record SuplementoRequisicao(
		UUID id,
		UUID produtoId,
		@Size(max=120) String nome,
		@NotBlank @Size(max=80) String dose,
		@Size(max=60) String forma,
		@Size(max=120) String frequencia,
		@Size(max=6) List<@Pattern(regexp="^([01]\\d|2[0-3]):[0-5]\\d$") String> horarios,
		@NotNull LocalDate inicio,
		LocalDate fim) {
}
