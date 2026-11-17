package com.br.dietamais.dietas.dominio;

import com.br.dietamais.dietas.api.AlertaRestricao;
import com.br.dietamais.pacientes.api.RestricaoSaude;
import com.br.dietamais.pacientes.api.TipoRestricaoSaude;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * RN-DIE-14: alertas de um item. Para cada restrição ALERGIA ou INTOLERANCIA da ficha, na ordem da ficha, um alerta por marcador em comum com o alimento
 * (na ordem dos marcadores da restrição). Só alergia exige confirmação. Os mesmos vetores do app (`contrato/calculos/alertas-restricao.json`).
 */
public final class CalculoAlertas {
	private CalculoAlertas(){}

	public static List<AlertaRestricao> alertasItem(List<RestricaoSaude> restricoes,Set<String> marcadoresDoItem){
		var r=new ArrayList<AlertaRestricao>();
		if(restricoes==null||marcadoresDoItem==null||marcadoresDoItem.isEmpty())return r;
		for(var restricao:restricoes){
			if(restricao.tipo()!=TipoRestricaoSaude.ALERGIA&&restricao.tipo()!=TipoRestricaoSaude.INTOLERANCIA)continue;
			if(restricao.marcadores()==null)continue;
			for(var marcador:restricao.marcadores())
				if(marcadoresDoItem.contains(marcador))r.add(new AlertaRestricao(restricao.id(),restricao.tipo().name(),restricao.nome(),marcador,restricao.tipo()==TipoRestricaoSaude.ALERGIA));
		}
		return r;
	}
}
