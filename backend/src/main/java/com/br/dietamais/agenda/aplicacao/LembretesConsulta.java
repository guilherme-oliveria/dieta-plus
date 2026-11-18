package com.br.dietamais.agenda.aplicacao;

import com.br.dietamais.agenda.api.AntecedenciaLembrete;
import com.br.dietamais.agenda.infra.ConsultaStore;
import com.br.dietamais.comum.api.Perfil;
import com.br.dietamais.notificacoes.api.*;
import java.time.*;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * RN-AGE-02 / RN-AGE-05 / RN-NOT-01: aviso antes da consulta, para o paciente e para a nutri, na antecedência que a nutri escolheu
 * (2 h, 1 dia ou 2 dias; padrão 1 dia) e, se ela quiser, outro 1 h antes (D-08). O texto não traz horário nem local (RN-NOT-02);
 * quem desligou o tipo CONSULTA_AMANHA fica só com o histórico (RN-NOT-03, no envio do push).
 * A chave de dedup inclui o início: remarcar gera um aviso novo. Consulta marcada já dentro da última hora só recebe o lembrete de 1 h.
 */
@Service
public class LembretesConsulta {
	/** Maior antecedência possível: a janela da busca. */
	static final Duration JANELA=Duration.ofDays(2);
	static final Duration EXTRA=Duration.ofHours(1);
	private final ConsultaStore consultas;private final RegistrarAviso avisos;private final Clock relogio;
	public LembretesConsulta(ConsultaStore consultas,RegistrarAviso avisos,Clock relogio){this.consultas=consultas;this.avisos=avisos;this.relogio=relogio;}

	@Scheduled(fixedDelayString="${dieta.avisos.intervalo-consultas:PT15M}",initialDelayString="${dieta.avisos.intervalo-consultas:PT15M}")
	public void executarJob(){
		try { executar(); }
		catch (RuntimeException erro) { LoggerFactory.getLogger(LembretesConsulta.class).warn("Falha técnica no job de lembrete de consulta."); }
	}
	public int executar(){return executar(relogio.instant());}

	/** Relógio explícito para teste; devolve quantos lembretes (por consulta) entraram nesta rodada, contando os já deduplicados. */
	public int executar(Instant agora){
		int n=0;
		for(var c:consultas.aComecar(agora,agora.plus(JANELA))){
			boolean naUltimaHora=!c.inicio().isAfter(agora.plus(EXTRA));
			var chave="consulta:"+c.id()+":"+c.inicio().getEpochSecond();
			if(c.lembreteExtraUmaHora()&&naUltimaHora){avisar(c,chave+":1h");n++;}
			else if(!c.inicio().isAfter(agora.plus(antecedencia(c.antecedencia())))){avisar(c,chave);n++;}
		}
		return n;
	}
	static Duration antecedencia(AntecedenciaLembrete a){
		return switch(a){case DUAS_HORAS->Duration.ofHours(2);case UM_DIA->Duration.ofDays(1);case DOIS_DIAS->Duration.ofDays(2);};
	}
	private void avisar(ConsultaStore.Lembrete c,String chave){
		avisos.registrar(new NovoAviso(Perfil.PACIENTE,c.pacienteId(),TipoAviso.CONSULTA_AMANHA,"/agenda",chave));
		avisos.registrar(new NovoAviso(Perfil.NUTRICIONISTA,c.nutricionistaId(),TipoAviso.CONSULTA_AMANHA,"/agenda",chave));
	}
}
