package com.br.dietamais.prescricoes.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.prescricoes.api.OrientacaoResposta;
import com.br.dietamais.prescricoes.infra.OrientacaoStore;
import java.util.List;
import org.springframework.stereotype.Component;

/** Coleção `orientacoes` do sync (BE-76): a nutri e o paciente puxam; o paciente continua recebendo com a assinatura da nutri inativa (RN-ASS-05). */
@Component
public class FonteOrientacoesSync implements FonteSync<OrientacaoResposta> {
	private final DiarioSync diario;private final OrientacaoStore orientacoes;
	public FonteOrientacoesSync(DiarioSync diario,OrientacaoStore orientacoes){this.diario=diario;this.orientacoes=orientacoes;}
	@Override public ColecaoSync colecao(){return ColecaoSync.ORIENTACOES;}
	@Override public List<ReferenciaSync> mudancasDesde(long seq,ContextoUsuario usuario,int limite){return diario.mudancasDesde(colecao(),seq,usuario,limite);}
	@Override public OrientacaoResposta carregar(ReferenciaSync referencia,ContextoUsuario usuario){
		if(usuario.perfil()==Perfil.NUTRICIONISTA)return orientacoes.daNutri(referencia.id(),usuario.exigirNutricionista(),false);
		return orientacoes.doPaciente(referencia.id(),usuario.exigirPaciente());
	}
}
