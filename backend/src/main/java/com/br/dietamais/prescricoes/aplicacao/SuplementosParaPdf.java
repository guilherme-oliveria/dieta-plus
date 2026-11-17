package com.br.dietamais.prescricoes.aplicacao;

import com.br.dietamais.prescricoes.api.*;
import com.br.dietamais.prescricoes.infra.SuplementoStore;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SuplementosParaPdf implements ConsultarSuplementosParaPdf {
	private final SuplementoStore suplementos;
	public SuplementosParaPdf(SuplementoStore suplementos){this.suplementos=suplementos;}
	@Override public List<SuplementoResposta> ativosDaNutri(UUID nutri,UUID paciente,LocalDate hoje){
		return suplementos.doPacienteDaNutri(paciente,nutri).stream().filter(s->!s.inicio().isAfter(hoje)&&(s.fim()==null||!s.fim().isBefore(hoje))).toList();
	}
}
