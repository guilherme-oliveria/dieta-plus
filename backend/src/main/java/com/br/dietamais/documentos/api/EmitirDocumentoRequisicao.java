package com.br.dietamais.documentos.api;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * `POST /pacientes/{id}/documentos` (RN-DOC-02). Com `consultaId` (consulta REALIZADA do paciente) data e horário vêm dela.
 * `texto` opcional substitui o texto do modelo já preenchido; o texto final é gravado e nunca mais muda.
 */
public record EmitirDocumentoRequisicao(
		UUID id,
		@NotNull UUID modeloId,
		UUID consultaId,
		LocalDate data,
		@Pattern(regexp="^([01]\\d|2[0-3]):[0-5]\\d$") String horario,
		@Size(max=5000) String texto) {
}
