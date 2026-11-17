package com.br.dietamais.metas.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.metas.api.*;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Mutação CRIAR do paciente em {@code POST /sync} (RN-MET-02): as mesmas regras e a mesma idempotência de {@code POST /eu/registros-meta}. */
@Component
public class ManipuladoresMetas implements ManipuladorMutacao {
	private final JsonMapper mapper;private final Validator validador;private final RegistrarMetas registros;
	public ManipuladoresMetas(JsonMapper mapper,Validator validador,RegistrarMetas registros){this.mapper=mapper;this.validador=validador;this.registros=registros;}
	@Override public String entidade(){return "registrosMeta";}
	@Override public ResultadoMutacao aplicar(MutacaoSync m,ContextoUsuario usuario){
		usuario.exigirPaciente();
		if(m.operacao()!=OperacaoSync.CRIAR||m.id()==null||m.dados()==null||m.dados().isNull())throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-SYN-03");
		RegistroMetaRequisicao r;
		try{
			var lido=mapper.treeToValue(m.dados(),RegistroMetaRequisicao.class);
			r=new RegistroMetaRequisicao(m.id(),lido.metaId(),lido.data(),lido.valor(),lido.registradoEm());
		}catch(RuntimeException e){throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,null);}
		if(!validador.validate(r).isEmpty())throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,null);
		registros.registrar(r);
		return ResultadoMutacao.aplicada(m.idMutacao(),1L);
	}
}
