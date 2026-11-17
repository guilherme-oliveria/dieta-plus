package com.br.dietamais.rotina.api;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckinResposta(
	@NotNull UUID id,
	@NotNull UUID pacienteId,
	@NotNull UUID dietaId,
	@NotNull UUID refeicaoId,
	@NotNull LocalDate data,
	@NotNull StatusCheckin status,
	@NotNull List<@NotNull @Valid TrocaCheckin> trocas,
	@NotNull Instant registradoEm,
	@Schema(nullable=true) Integer fomeAntes,
	@Schema(nullable=true) Integer sensacaoDepois) {
	public CheckinResposta(UUID id,UUID pacienteId,UUID dietaId,UUID refeicaoId,LocalDate data,StatusCheckin status,List<TrocaCheckin> trocas,Instant registradoEm){this(id,pacienteId,dietaId,refeicaoId,data,status,trocas,registradoEm,null,null);}
}
