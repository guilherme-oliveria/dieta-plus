package com.br.dietamais.alimentos;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.comum.TesteIsolamento;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** BE-69: marcadores novos (amendoim, frutos do mar, ovo, soja) sugeridos por nome no catálogo e aceitos nos alimentos da nutri. */
class MarcadoresRestricaoApiTest extends TesteIsolamento {
	JsonNode primeiro(String token,String busca) throws Exception {
		var itens=json(requisitar("GET","/alimentos?busca="+busca.replace(" ","%20")+"&tamanho=100",token,null)).path("itens");
		assertThat(itens).as(busca).isNotEmpty();return itens.get(0);
	}
	@Test void catalogoSugereOsMarcadoresNovosSoPeloNomeDoAlimento() throws Exception {
		var a=cadastrarNutri();
		assertThat(primeiro(a.token(),"Creme de amendoim").path("marcadores").toString()).contains("AMENDOIM");
		assertThat(primeiro(a.token(),"Camarão").path("marcadores").toString()).contains("FRUTOS_DO_MAR");
		assertThat(primeiro(a.token(),"Ovo de galinha").path("marcadores").toString()).contains("OVO");
		assertThat(primeiro(a.token(),"Óleo de soja").path("marcadores").toString()).contains("SOJA");
		// o grupo "Ovo e outras carnes" não vale: hambúrguer não leva OVO
		for(JsonNode item:json(requisitar("GET","/alimentos?busca=Hambúrguer&tamanho=100",a.token(),null)).path("itens"))
			if(item.path("nome").asString().startsWith("Ovo e outras carnes"))assertThat(item.path("marcadores").toString()).doesNotContain("OVO");
		// os marcadores antigos continuam
		assertThat(primeiro(a.token(),"Catupiry").path("marcadores").toString()).contains("CONTEM_LACTOSE");
	}
	@Test void nutriEditaOsMarcadoresNovosNaCopiaPropriaEOutraNutriNaoVe() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();
		String grupo=json(requisitar("GET","/grupos-alimentos",a.token(),null)).path("itens").get(0).path("id").asString();
		var corpo=new HashMap<String,Object>(Map.of("id","019a0000-0002-7000-8000-000000009001","grupoId",grupo,"nome","Alimento novo ficticio","medidaCaseira","1 unidade","gramasPorPorcao",10));
		corpo.put("marcadores",List.of("SOJA","OVO","SOJA","FRUTOS_DO_MAR","AMENDOIM"));
		var criado=requisitar("POST","/alimentos",a.token(),corpo);assertThat(criado.statusCode()).as(criado.body()).isEqualTo(201);
		assertThat(json(criado).path("marcadores")).hasSize(4);
		assertThat(json(requisitar("GET","/alimentos?busca=Alimento%20novo%20ficticio&semMarcador=SOJA",a.token(),null)).path("total").asLong()).isZero();
		assertThat(json(requisitar("GET","/alimentos?busca=Alimento%20novo%20ficticio",b.token(),null)).path("total").asLong()).isZero();
	}
}
