package com.br.dietamais.agenda.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Projeção da consulta para o paciente (REST `GET /eu/consultas` e coleção `consultas` do sync, BE-C9, H-02, RN-AGE-04).
 * Não traz `observacaoInterna`, `status` da nutri nem financeiro. `linkOnline` só em consulta ONLINE agendada.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsultaPacienteProjecaoResposta(
		@NotNull UUID id,
		@NotNull Instant inicio,
		@NotNull @Positive Integer duracaoMin,
		@NotNull TipoConsulta tipo,
		@NotNull ModalidadeConsulta modalidade,
		@Schema(nullable=true) String lugar,
		@Schema(nullable=true,description="https; só consulta ONLINE agendada") String linkOnline,
		@Schema(nullable=true,description="Até 2.000 caracteres") String orientacaoPaciente,
		@NotNull StatusConsultaPaciente status,
		@NotNull @PositiveOrZero Long versao) {
}
