package com.br.dietamais.rotina.api;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CheckinRequisicao(
	UUID id,
	@NotNull UUID dietaId,
	@NotNull UUID refeicaoId,
	@NotNull LocalDate data,
	@NotNull StatusCheckin status,
	List<@NotNull @Valid TrocaCheckin> trocas,
	@NotNull Instant registradoEm,
	@Schema(nullable=true,description="Fome antes da refeição, 1 a 5 (RN-ROT-01, 1.4.0)") @Min(1) @Max(5) Integer fomeAntes,
	@Schema(nullable=true,description="Sensação depois da refeição, 1 a 5 (RN-ROT-01, 1.4.0)") @Min(1) @Max(5) Integer sensacaoDepois) {
	public CheckinRequisicao(UUID id,UUID dietaId,UUID refeicaoId,LocalDate data,StatusCheckin status,List<TrocaCheckin> trocas,Instant registradoEm){this(id,dietaId,refeicaoId,data,status,trocas,registradoEm,null,null);}
}
