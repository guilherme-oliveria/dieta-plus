package com.br.dietamais.agenda.api;

import jakarta.validation.constraints.*;
import java.time.Instant;

/**
 * Campos ausentes ficam como estão; texto vazio limpa o campo. {@code status}: transições da RN-AGE-03 (AGENDADA vai para REALIZADA, FALTA ou CANCELADA;
 * REALIZADA e FALTA se corrigem entre si; CANCELADA é final). Consulta que não está AGENDADA só aceita status e os textos.
 */
public record AtualizarConsultaRequisicao(
		Instant inicio,
		@Min(5) @Max(480) Integer duracaoMin,
		TipoConsulta tipo,
		@Size(max=200) String lugar,
		StatusConsulta status,
		ModalidadeConsulta modalidade,
		@Size(max=300) String linkOnline,
		@Size(max=2000) String orientacaoPaciente,
		@Size(max=2000) String observacaoInterna) {
	public AtualizarConsultaRequisicao(Instant inicio,Integer duracaoMin,TipoConsulta tipo,String lugar,StatusConsulta status){this(inicio,duracaoMin,tipo,lugar,status,null,null,null,null);}
}
