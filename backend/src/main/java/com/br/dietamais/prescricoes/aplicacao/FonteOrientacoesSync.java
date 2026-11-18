package com.br.dietamais.prescricoes.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.prescricoes.api.OrientacaoResposta;
import com.br.dietamais.prescricoes.infra.OrientacaoStore;
import java.util.List;
import org.springframework.stereotype.Component;

/** Coleção `orientacoes` do sync (BE-76): a nutri e o paciente puxam; o paciente continua recebendo com a assinatura da nutri inativa (RN-ASS-05). */
@Component
public class FonteOrientacoesSync implements FonteSync<OrientacaoResposta> {
	private final DiarioSync diario;private final OrientacaoStore orientacoes;private final AuditoriaClinica auditoria;
	public FonteOrientacoesSync(DiarioSync diario,OrientacaoStore orientacoes,AuditoriaClinica auditoria){this.diario=diario;this.orientacoes=orientacoes;this.auditoria=auditoria;}
	@Override public ColecaoSync colecao(){return ColecaoSync.ORIENTACOES;}
	@Override public List<ReferenciaSync> mudancasDesde(long seq,ContextoUsuario usuario,int limite){return diario.mudancasDesde(colecao(),seq,usuario,limite);}
	@Override public OrientacaoResposta carregar(ReferenciaSync referencia,ContextoUsuario usuario){
		if(usuario.perfil()==Perfil.NUTRICIONISTA){
			var dados=orientacoes.daNutri(referencia.id(),usuario.exigirNutricionista(),false);
			// BE-84: leitura clínica pela nutri vai para o log_acesso
			if(dados!=null)auditoria.leitura(new ReferenciaClinica("orientacao",dados.id(),usuario.exigirNutricionista(),dados.pacienteId(),usuario.organizacaoId()));
			return dados;
		}
		return orientacoes.doPaciente(referencia.id(),usuario.exigirPaciente());
	}
}
