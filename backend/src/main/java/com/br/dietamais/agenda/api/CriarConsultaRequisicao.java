package com.br.dietamais.agenda.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

/** {@code pacienteId} é o id da ficha; a posse é conferida no backend (outra nutri = 404). */
public record CriarConsultaRequisicao(
		UUID id,
		@NotNull UUID pacienteId,
		@NotNull Instant inicio,
		@NotNull @Min(5) @Max(480) Integer duracaoMin,
		@NotNull TipoConsulta tipo,
		@Size(max=200) String lugar,
		/** Ausente: PRESENCIAL (ou ONLINE quando o tipo legado é ONLINE). */
		ModalidadeConsulta modalidade,
		@Size(max=300) String linkOnline,
		@Size(max=2000) String orientacaoPaciente,
		@Size(max=2000) String observacaoInterna) {
	public CriarConsultaRequisicao(UUID id,UUID pacienteId,Instant inicio,Integer duracaoMin,TipoConsulta tipo,String lugar){this(id,pacienteId,inicio,duracaoMin,tipo,lugar,null,null,null,null);}
}
