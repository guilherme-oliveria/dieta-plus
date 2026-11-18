package com.br.dietamais.agenda.aplicacao;

import com.br.dietamais.agenda.api.*;
import com.br.dietamais.agenda.infra.PreferenciaAgendaStore;
import com.br.dietamais.comum.api.UsuarioAtual;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RN-AGE-05, D-08: a antecedência do lembrete é da nutri e vale também para os pacientes dela. Sem ETag: o último PUT vale. */
@Service
public class GerenciarPreferenciasAgenda {
	private final PreferenciaAgendaStore preferencias;private final UsuarioAtual usuario;private final Clock relogio;
	public GerenciarPreferenciasAgenda(PreferenciaAgendaStore preferencias,UsuarioAtual usuario,Clock relogio){this.preferencias=preferencias;this.usuario=usuario;this.relogio=relogio;}

	@Transactional(readOnly=true)
	public PreferenciasAgendaResposta consultar(){return preferencias.daNutri(usuario.obter().exigirNutricionista());}

	/** Como toda escrita da nutri, o filtro de contexto já exige assinatura válida (RN-ASS-04). */
	@Transactional
	public PreferenciasAgendaResposta salvar(PreferenciasAgendaRequisicao r){
		var nutri=usuario.obter().exigirNutricionista();
		var nova=new PreferenciasAgendaResposta(r.antecedencia(),r.lembreteExtraUmaHora());
		preferencias.salvar(nutri,nova,relogio.instant());
		return nova;
	}
}
