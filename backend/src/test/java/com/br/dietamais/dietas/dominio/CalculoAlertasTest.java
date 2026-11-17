package com.br.dietamais.dietas.dominio;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.pacientes.api.RestricaoSaude;
import com.br.dietamais.pacientes.api.TipoRestricaoSaude;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** BE-70, RN-DIE-14: a função do servidor tem de bater com os vetores compartilhados com o app. */
class CalculoAlertasTest {
	static Stream<JsonNode> casos() throws Exception {
		try(var in=CalculoAlertasTest.class.getResourceAsStream("/contrato/calculos/alertas-restricao.json")){
			var raiz=JsonMapper.builder().build().readTree(new String(in.readAllBytes(),StandardCharsets.UTF_8));
			var l=new ArrayList<JsonNode>();raiz.path("casos").forEach(l::add);return l.stream();
		}
	}
	@ParameterizedTest(name="{0}") @MethodSource("casos") void bateComOsVetores(JsonNode caso){
		var e=caso.path("entrada");var restricoes=new ArrayList<RestricaoSaude>();
		for(var r:e.path("restricoes")){
			var marcadores=new ArrayList<String>();r.path("marcadores").forEach(m->marcadores.add(m.asString()));
			restricoes.add(new RestricaoSaude(UUID.fromString(r.path("id").asString()),TipoRestricaoSaude.valueOf(r.path("tipo").asString()),r.path("nome").asString(),null,null,marcadores));
		}
		var item=new HashSet<String>();e.path("item").path("marcadores").forEach(m->item.add(m.asString()));
		var alertas=CalculoAlertas.alertasItem(restricoes,item);
		var esperado=caso.path("esperado").path("alertas");
		assertThat(alertas).hasSize(esperado.size());
		for(int i=0;i<esperado.size();i++){
			assertThat(alertas.get(i).restricaoId().toString()).isEqualTo(esperado.get(i).path("restricaoId").asString());
			assertThat(alertas.get(i).tipo()).isEqualTo(esperado.get(i).path("tipo").asString());
			assertThat(alertas.get(i).marcador()).isEqualTo(esperado.get(i).path("marcador").asString());
			assertThat(alertas.get(i).exigeConfirmacao()).isEqualTo(esperado.get(i).path("exigeConfirmacao").asBoolean());
		}
	}
}
