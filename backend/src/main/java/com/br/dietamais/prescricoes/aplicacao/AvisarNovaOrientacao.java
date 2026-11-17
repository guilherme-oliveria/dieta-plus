package com.br.dietamais.prescricoes.aplicacao;

import com.br.dietamais.comum.api.Perfil;
import com.br.dietamais.notificacoes.api.*;
import com.br.dietamais.prescricoes.api.OrientacaoCriada;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** RN-ORI-01 e RN-NOT-01 e 02: avisa o paciente da orientação nova com texto genérico (nunca o título nem o conteúdo), só depois do commit. */
@Component
public class AvisarNovaOrientacao {
	private final RegistrarAviso avisos;
	public AvisarNovaOrientacao(RegistrarAviso avisos){this.avisos=avisos;}
	@TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true)
	void aoCriar(OrientacaoCriada e){
		avisos.registrar(new NovoAviso(Perfil.PACIENTE,e.pacienteId(),TipoAviso.NOVA_ORIENTACAO,"/orientacoes","orientacao:"+e.orientacaoId()));
	}
}
