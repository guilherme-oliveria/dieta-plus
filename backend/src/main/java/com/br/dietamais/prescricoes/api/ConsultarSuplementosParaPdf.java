package com.br.dietamais.prescricoes.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Suplementos ativos de um paciente da nutri para o PDF da dieta (BE-77): só os que valem na data (início até fim). */
public interface ConsultarSuplementosParaPdf {
	List<SuplementoResposta> ativosDaNutri(UUID nutri,UUID paciente,LocalDate hoje);
}
