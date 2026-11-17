package com.br.dietamais.agenda.api;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Consulta agendada pela nutri (RN-AGE-01). {@code pacienteId} é o id da ficha. {@code lugar}: endereço ou link da videochamada. */
public record ConsultaResposta(
		@NotNull UUID id,
		@NotNull UUID pacienteId,
		@NotNull UUID vinculoId,
		@NotNull Instant inicio,
		@NotNull @Positive Integer duracaoMin,
		@NotNull TipoConsulta tipo,
		String lugar,
		@NotNull StatusConsulta status,
		@NotNull @PositiveOrZero Long versao,
		@NotNull Instant criadoEm,
		@NotNull Instant atualizadoEm,
		/** BE-71 (RN-AGE-04): `PRESENCIAL` ou `ONLINE`. O app 1.3.x ignora. */
		@NotNull ModalidadeConsulta modalidade,
		/** Só consulta ONLINE; no paciente, só enquanto AGENDADA. */
		@JsonInclude(JsonInclude.Include.NON_NULL) @Schema(nullable=true,requiredMode=Schema.RequiredMode.NOT_REQUIRED) String linkOnline,
		/** Texto da nutri para o paciente (até 2.000). */
		@JsonInclude(JsonInclude.Include.NON_NULL) @Schema(nullable=true,requiredMode=Schema.RequiredMode.NOT_REQUIRED) String orientacaoPaciente,
		/** Só a nutri dona recebe; nunca na projeção do paciente (RN-AGE-04). */
		@JsonInclude(JsonInclude.Include.NON_NULL) @Schema(nullable=true,requiredMode=Schema.RequiredMode.NOT_REQUIRED) String observacaoInterna) {
	public ConsultaResposta(UUID id,UUID pacienteId,UUID vinculoId,Instant inicio,Integer duracaoMin,TipoConsulta tipo,String lugar,StatusConsulta status,Long versao,Instant criadoEm,Instant atualizadoEm){
		this(id,pacienteId,vinculoId,inicio,duracaoMin,tipo,lugar,status,versao,criadoEm,atualizadoEm,ModalidadeConsulta.PRESENCIAL,null,null,null);
	}
	/** Projeção do paciente (RN-AGE-03 e 04): REALIZADA e FALTA viram ENCERRADA, sem observação interna, link só em consulta online agendada. */
	public ConsultaResposta paraPaciente(){
		var visivel=status==StatusConsulta.REALIZADA||status==StatusConsulta.FALTA?StatusConsulta.ENCERRADA:status;
		return new ConsultaResposta(id,pacienteId,vinculoId,inicio,duracaoMin,tipo,lugar,visivel,versao,criadoEm,atualizadoEm,modalidade,
			status==StatusConsulta.AGENDADA&&modalidade==ModalidadeConsulta.ONLINE?linkOnline:null,orientacaoPaciente,null);
	}
}
