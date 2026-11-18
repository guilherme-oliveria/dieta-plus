package com.br.dietamais.metas.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.metas.api.*;
import com.br.dietamais.metas.infra.MetaStore;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Fontes sync de metas (BE-74): a nutri e o paciente recebem a meta; o registro vai do paciente para ele e para a nutri do vínculo ativo.
 * A leitura pela nutri fica no `log_acesso` (BE-84), como nas outras coleções clínicas.
 */
public final class FontesMetasSync {
	private FontesMetasSync(){}

	@Component
	public static class Metas implements FonteSync<MetaResposta> {
		private final DiarioSync diario;private final MetaStore metas;private final AuditoriaClinica auditoria;
		public Metas(DiarioSync diario,MetaStore metas,AuditoriaClinica auditoria){this.diario=diario;this.metas=metas;this.auditoria=auditoria;}
		@Override public ColecaoSync colecao(){return ColecaoSync.METAS;}
		@Override public List<ReferenciaSync> mudancasDesde(long seq,ContextoUsuario usuario,int limite){return diario.mudancasDesde(colecao(),seq,usuario,limite);}
		@Override public MetaResposta carregar(ReferenciaSync referencia,ContextoUsuario usuario){
			if(usuario.perfil()==Perfil.NUTRICIONISTA){
				var m=metas.daNutri(referencia.id(),usuario.exigirNutricionista(),false);
				if(m!=null)auditoria.leitura(new ReferenciaClinica("meta_habito",m.id(),usuario.exigirNutricionista(),m.pacienteId(),usuario.organizacaoId()));
				return m;
			}
			return metas.doPaciente(referencia.id(),usuario.exigirPaciente());
		}
	}

	@Component
	public static class Registros implements FonteSync<RegistroMetaResposta> {
		private final DiarioSync diario;private final MetaStore metas;private final AuditoriaClinica auditoria;
		public Registros(DiarioSync diario,MetaStore metas,AuditoriaClinica auditoria){this.diario=diario;this.metas=metas;this.auditoria=auditoria;}
		@Override public ColecaoSync colecao(){return ColecaoSync.REGISTROS_META;}
		@Override public List<ReferenciaSync> mudancasDesde(long seq,ContextoUsuario usuario,int limite){return diario.mudancasDesde(colecao(),seq,usuario,limite);}
		@Override public RegistroMetaResposta carregar(ReferenciaSync referencia,ContextoUsuario usuario){
			if(usuario.perfil()==Perfil.NUTRICIONISTA){
				var r=metas.registroDoPacienteDaNutri(referencia.id(),usuario.exigirNutricionista());
				if(r!=null)auditoria.leitura(new ReferenciaClinica("registro_meta",r.id(),usuario.exigirNutricionista(),referencia.pacienteId(),usuario.organizacaoId()));
				return r;
			}
			return metas.registro(referencia.id(),usuario.exigirPaciente());
		}
	}
}
