package com.br.dietamais.assinaturas.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Troca para plano menor (ou só de periodicidade) agendada para o próximo vencimento (BE-81b, RN-ASS-10, D-11). */
@Schema(description="Troca agendada para o próximo vencimento; até lá vale o plano atual.")
public record TrocaPlanoAgendada(@NotBlank String planoCodigo,@NotNull Periodicidade periodicidade,@NotNull LocalDate aPartirDe) { }
