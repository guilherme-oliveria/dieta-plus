package com.br.dietamais.prescricoes.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.prescricoes.api.SuplementoResposta;
import com.br.dietamais.prescricoes.infra.SuplementoStore;
import java.util.List;
import org.springframework.stereotype.Component;

/** Coleção `prescricoesSuplemento` do sync (BE-75): a nutri e o paciente puxam; o paciente recebe sem o produto e continua recebendo com a assinatura da nutri inativa (RN-ASS-05). */
@Component
public class FonteSuplementosSync implements FonteSync<SuplementoResposta> {
	private final DiarioSync diario;private final SuplementoStore suplementos;
	public FonteSuplementosSync(DiarioSync diario,SuplementoStore suplementos){this.diario=diario;this.suplementos=suplementos;}
	@Override public ColecaoSync colecao(){return ColecaoSync.SUPLEMENTOS;}
	@Override public List<ReferenciaSync> mudancasDesde(long seq,ContextoUsuario usuario,int limite){return diario.mudancasDesde(colecao(),seq,usuario,limite);}
	@Override public SuplementoResposta carregar(ReferenciaSync referencia,ContextoUsuario usuario){
		if(usuario.perfil()==Perfil.NUTRICIONISTA)return suplementos.daNutri(referencia.id(),usuario.exigirNutricionista(),false);
		return suplementos.doPaciente(referencia.id(),usuario.exigirPaciente());
	}
}
