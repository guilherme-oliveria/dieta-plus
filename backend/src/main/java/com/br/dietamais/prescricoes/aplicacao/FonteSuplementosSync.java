package com.br.dietamais.prescricoes.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.prescricoes.api.SuplementoResposta;
import com.br.dietamais.prescricoes.infra.SuplementoStore;
import java.util.List;
import org.springframework.stereotype.Component;

/** Coleção `prescricoesSuplemento` do sync (BE-75): a nutri e o paciente puxam; o paciente recebe sem o produto e continua recebendo com a assinatura da nutri inativa (RN-ASS-05). */
@Component
public class FonteSuplementosSync implements FonteSync<SuplementoResposta> {
	private final DiarioSync diario;private final SuplementoStore suplementos;private final AuditoriaClinica auditoria;
	public FonteSuplementosSync(DiarioSync diario,SuplementoStore suplementos,AuditoriaClinica auditoria){this.diario=diario;this.suplementos=suplementos;this.auditoria=auditoria;}
	@Override public ColecaoSync colecao(){return ColecaoSync.SUPLEMENTOS;}
	@Override public List<ReferenciaSync> mudancasDesde(long seq,ContextoUsuario usuario,int limite){return diario.mudancasDesde(colecao(),seq,usuario,limite);}
	@Override public SuplementoResposta carregar(ReferenciaSync referencia,ContextoUsuario usuario){
		if(usuario.perfil()==Perfil.NUTRICIONISTA){
			var dados=suplementos.daNutri(referencia.id(),usuario.exigirNutricionista(),false);
			// BE-84: leitura clínica pela nutri vai para o log_acesso
			if(dados!=null)auditoria.leitura(new ReferenciaClinica("prescricao_suplemento",dados.id(),usuario.exigirNutricionista(),dados.pacienteId(),usuario.organizacaoId()));
			return dados;
		}
		return suplementos.doPaciente(referencia.id(),usuario.exigirPaciente());
	}
}
