package com.br.dietamais.pacientes.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.pacientes.api.*;
import com.br.dietamais.pacientes.infra.FichaPacienteStore;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConsultaRestricoesParaDietaPropria implements ConsultaRestricoesParaDieta {
	private final UsuarioAtual usuario;private final FichaPacienteStore fichas;
	public ConsultaRestricoesParaDietaPropria(UsuarioAtual usuario,FichaPacienteStore fichas){this.usuario=usuario;this.fichas=fichas;}
	@Override public List<RestricaoSaude> restricoes(UUID paciente){
		var ficha=fichas.consultar(paciente,usuario.obter().exigirNutricionista(),false);
		if(ficha==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		return ficha.restricoesSaude()==null?List.of():ficha.restricoesSaude();
	}
}
