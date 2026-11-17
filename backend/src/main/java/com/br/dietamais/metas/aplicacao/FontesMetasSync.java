package com.br.dietamais.metas.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.metas.api.*;
import com.br.dietamais.metas.infra.MetaStore;
import java.util.List;
import org.springframework.stereotype.Component;

/** Fontes sync de metas (BE-74): a nutri e o paciente recebem a meta; o registro vai do paciente para ele e para a nutri do vínculo ativo. */
public final class FontesMetasSync {
	private FontesMetasSync(){}

	@Component
	public static class Metas implements FonteSync<MetaResposta> {
		private final DiarioSync diario;private final MetaStore metas;
		public Metas(DiarioSync diario,MetaStore metas){this.diario=diario;this.metas=metas;}
		@Override public ColecaoSync colecao(){return ColecaoSync.METAS;}
		@Override public List<ReferenciaSync> mudancasDesde(long seq,ContextoUsuario usuario,int limite){return diario.mudancasDesde(colecao(),seq,usuario,limite);}
		@Override public MetaResposta carregar(ReferenciaSync referencia,ContextoUsuario usuario){
			if(usuario.perfil()==Perfil.NUTRICIONISTA)return metas.daNutri(referencia.id(),usuario.exigirNutricionista(),false);
			return metas.doPaciente(referencia.id(),usuario.exigirPaciente());
		}
	}

	@Component
	public static class Registros implements FonteSync<RegistroMetaResposta> {
		private final DiarioSync diario;private final MetaStore metas;
		public Registros(DiarioSync diario,MetaStore metas){this.diario=diario;this.metas=metas;}
		@Override public ColecaoSync colecao(){return ColecaoSync.REGISTROS_META;}
		@Override public List<ReferenciaSync> mudancasDesde(long seq,ContextoUsuario usuario,int limite){return diario.mudancasDesde(colecao(),seq,usuario,limite);}
		@Override public RegistroMetaResposta carregar(ReferenciaSync referencia,ContextoUsuario usuario){
			if(usuario.perfil()==Perfil.NUTRICIONISTA)return metas.registroDoPacienteDaNutri(referencia.id(),usuario.exigirNutricionista());
			return metas.registro(referencia.id(),usuario.exigirPaciente());
		}
	}
}
