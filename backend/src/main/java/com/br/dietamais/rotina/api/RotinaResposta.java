package com.br.dietamais.rotina.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Registros do paciente num período (BE-38), no mesmo formato do /sync; a web, sem banco local, lê o "Hoje" por aqui. */
public record RotinaResposta(
	@NotNull List<@NotNull @Valid CheckinResposta> checkins,
	@NotNull List<@NotNull @Valid AguaResposta> agua,
	@NotNull List<@NotNull @Valid PesoResposta> pesos,
	@io.swagger.v3.oas.annotations.media.Schema(nullable=true) List<@NotNull @Valid RegistroForaDoPlanoResposta> registrosForaDoPlano) {
	public RotinaResposta(List<CheckinResposta> checkins,List<AguaResposta> agua,List<PesoResposta> pesos){this(checkins,agua,pesos,null);}
}
