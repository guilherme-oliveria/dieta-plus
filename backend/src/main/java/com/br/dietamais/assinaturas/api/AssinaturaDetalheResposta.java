package com.br.dietamais.assinaturas.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tela de assinatura da nutri (BE-26). {@code carenciaAte} só vem em {@code EM_ATRASO}: depois dela a nutri fica só leitura (RN-ASS-04).
 * {@code planosDisponiveis} são os pagos; a contratação acontece no checkout hospedado (RN-ASS-06).
 */
public record AssinaturaDetalheResposta(
		@NotNull UUID organizacaoId,
		@NotNull PlanoResposta plano,
		@NotNull StatusAssinatura status,
		@NotNull @PositiveOrZero Integer pacientesOcupados,
		@Schema(nullable=true) Instant fimPeriodoEm,
		@Schema(nullable=true) Instant carenciaAte,
		@NotNull Boolean pagamentoDisponivel,
		@NotNull List<@jakarta.validation.Valid PlanoPublicoResposta> planosDisponiveis,
		@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Schema(nullable=true) Periodicidade periodicidade,
		@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Schema(nullable=true) @PositiveOrZero Integer diasRestantesTeste,
		@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Schema(nullable=true) Boolean exibePrecoEBotao,
		@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Schema(nullable=true,description="BE-81b: troca para plano menor agendada (RN-ASS-10)") @jakarta.validation.Valid TrocaPlanoAgendada trocaAgendada) {
	/** Forma da 1.3, sem os campos da 1.4 (RN-ASS-10 e 11). */
	public AssinaturaDetalheResposta(UUID organizacaoId,PlanoResposta plano,StatusAssinatura status,Integer pacientesOcupados,Instant fimPeriodoEm,Instant carenciaAte,Boolean pagamentoDisponivel,List<PlanoPublicoResposta> planosDisponiveis){
		this(organizacaoId,plano,status,pacientesOcupados,fimPeriodoEm,carenciaAte,pagamentoDisponivel,planosDisponiveis,null,null,null,null);
	}
	public AssinaturaDetalheResposta(UUID organizacaoId,PlanoResposta plano,StatusAssinatura status,Integer pacientesOcupados,Instant fimPeriodoEm,Instant carenciaAte,Boolean pagamentoDisponivel,
			List<PlanoPublicoResposta> planosDisponiveis,Periodicidade periodicidade,Integer diasRestantesTeste,Boolean exibePrecoEBotao){
		this(organizacaoId,plano,status,pacientesOcupados,fimPeriodoEm,carenciaAte,pagamentoDisponivel,planosDisponiveis,periodicidade,diasRestantesTeste,exibePrecoEBotao,null);
	}
}
