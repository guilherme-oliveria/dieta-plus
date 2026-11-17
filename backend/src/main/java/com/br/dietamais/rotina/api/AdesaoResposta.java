package com.br.dietamais.rotina.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record AdesaoResposta(@NotNull UUID pacienteId,@NotNull @Valid JanelaAdesao dias7,@NotNull @Valid JanelaAdesao dias30,
		@io.swagger.v3.oas.annotations.media.Schema(nullable=true,description="Médias da semana de fome antes e sensação depois (RN-ROT-06, 1.4.0)") @Valid HumorAdesao humor,
		@io.swagger.v3.oas.annotations.media.Schema(nullable=true,description="Registros fora do plano dos últimos 7 dias, do mais novo ao mais antigo (RN-ROT-06, 1.4.0)") java.util.List<@Valid RegistroForaDoPlanoResposta> foraDoPlano) {
	public AdesaoResposta(UUID pacienteId,JanelaAdesao dias7,JanelaAdesao dias30){this(pacienteId,dias7,dias30,null,null);}
}
