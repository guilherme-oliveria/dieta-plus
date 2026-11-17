package com.br.dietamais.dietas;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** BE-70 (H-01), RN-DIE-14: alertas por item, confirmação de alergia para publicar, restrições da dieta nova e snapshot sem dados de saúde. */
class AlertaAlergiaApiTest extends TesteComPaciente {
	JsonNode primeiro(String token,String busca) throws Exception {
		var itens=json(requisitar("GET","/alimentos?busca="+busca.replace(" ","%20")+"&tamanho=100",token,null)).path("itens");
		assertThat(itens).as(busca).isNotEmpty();return itens.get(0);
	}
	void definirRestricoes(String token,String ficha,List<Map<String,Object>> itens) throws Exception {
		var atual=requisitar("GET","/pacientes/"+ficha,token,null);String etag=atual.headers().firstValue("ETag").orElseThrow();
		var r=requisitar("PATCH","/pacientes/"+ficha,token,Map.of("restricoesSaude",itens),Map.of("If-Match",etag));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
	}
	Map<String,Object> restricao(String tipo,String nome,String... marcadores){
		var m=new LinkedHashMap<String,Object>();m.put("tipo",tipo);m.put("nome",nome);if(marcadores.length>0)m.put("marcadores",List.of(marcadores));return m;
	}

	@Test void alertaPorItemExigeConfirmacaoSoDeAlergiaEOSnapshotNaoLevaOAlerta() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);var b=cadastrarNutri();
		var amendoim=primeiro(a.token(),"Creme de amendoim");var catupiry=primeiro(a.token(),"Catupiry");var comum=alimentos(a.token(),1).stream().filter(x->!x.path("marcadores").toString().contains("AMENDOIM")&&!x.path("marcadores").toString().contains("CONTEM_LACTOSE")).findFirst().orElseThrow();
		definirRestricoes(a.token(),p.fichaId(),List.of(restricao("ALERGIA","Amendoim","AMENDOIM"),restricao("INTOLERANCIA","Lactose","CONTEM_LACTOSE"),restricao("CONDICAO","Diabetes")));
		var corpo=dieta("Com alerta",refeicao("Café",0,item(amendoim,"1"),item(catupiry,"1"),item(comum,"1")));
		var d=criarDieta(a.token(),p.fichaId(),corpo);
		var itens=d.path("refeicoes").get(0).path("itens");
		// alerta de alergia (exige confirmação), de intolerância (só avisa) e item sem alerta
		JsonNode deAmendoim=null,deQueijo=null,semAlerta=null;
		for(var i:itens){var nome=i.path("alimentoId").asString();
			if(nome.equals(amendoim.path("id").asString()))deAmendoim=i;else if(nome.equals(catupiry.path("id").asString()))deQueijo=i;else semAlerta=i;}
		assertThat(deAmendoim.path("alertas").get(0).path("tipo").asString()).isEqualTo("ALERGIA");assertThat(deAmendoim.path("alertas").get(0).path("exigeConfirmacao").asBoolean()).isTrue();
		assertThat(deQueijo.path("alertas").get(0).path("tipo").asString()).isEqualTo("INTOLERANCIA");assertThat(deQueijo.path("alertas").get(0).path("exigeConfirmacao").asBoolean()).isFalse();
		assertThat(semAlerta.has("alertas")).isFalse();
		// dieta nova nasce evitando os marcadores das alergias e intolerâncias da ficha
		assertThat(d.path("restricoes").toString()).contains("AMENDOIM").contains("CONTEM_LACTOSE");
		// publicar sem confirmar a alergia é recusado; confirmando, publica
		var recusa=requisitar("POST","/dietas/"+d.path("id").asString()+"/publicar",a.token(),null,Map.of("If-Match","\""+d.path("versao").asLong()+"\""));
		erro(recusa,422,"ALERGIA_NAO_CONFIRMADA");
		var confirmado=item(amendoim,"1");confirmado.put("alergiaConfirmada",true);
		var corpo2=dieta("Com alerta",refeicao("Café",0,confirmado,item(catupiry,"1")));
		var atualizada=requisitar("PUT","/dietas/"+d.path("id").asString(),a.token(),corpo2,Map.of("If-Match","\""+d.path("versao").asLong()+"\""));
		assertThat(atualizada.statusCode()).as(atualizada.body()).isEqualTo(200);
		assertThat(json(atualizada).path("refeicoes").get(0).path("itens").get(0).path("alergiaConfirmada").asBoolean()).isTrue();
		var snapshot=publicar(a.token(),json(atualizada));
		// o paciente não vê alerta, confirmação nem o nome da alergia
		var bruto=snapshot.toString();
		assertThat(bruto).doesNotContain("alertas").doesNotContain("alergiaConfirmada").doesNotContain("exigeConfirmacao").doesNotContain("Diabetes");
		var minha=requisitar("GET","/eu/dietas",p.token(),null);assertThat(minha.statusCode()).isEqualTo(200);
		assertThat(minha.body()).doesNotContain("alertas").doesNotContain("alergiaConfirmada").doesNotContain("Diabetes");
		// a lista de substituição do snapshot sai sem os alimentos com os marcadores evitados (RN-MOD-03)
		for(var alimento:snapshot.path("listaSubstituicao").path("alimentos"))assertThat(alimento.path("marcadores").toString()).doesNotContain("AMENDOIM").doesNotContain("CONTEM_LACTOSE");
		// outra nutri não vê a dieta
		erro(requisitar("GET","/dietas/"+d.path("id").asString(),b.token(),null),404,"RECURSO_NAO_ENCONTRADO");
	}

	@Test void apenasIntoleranciaPublicaSemConfirmacaoEDietaSemFichaComRestricaoNaoTemAlerta() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);var q=pacienteVinculado(a);
		var catupiry=primeiro(a.token(),"Catupiry");
		definirRestricoes(a.token(),p.fichaId(),List.of(restricao("INTOLERANCIA","Lactose","CONTEM_LACTOSE")));
		var d=criarDieta(a.token(),p.fichaId(),dieta("Intolerância",refeicao("Café",0,item(catupiry,"1"))));
		assertThat(d.path("refeicoes").get(0).path("itens").get(0).path("alertas")).hasSize(1);
		assertThat(requisitar("POST","/dietas/"+d.path("id").asString()+"/publicar",a.token(),null,Map.of("If-Match","\""+d.path("versao").asLong()+"\"")).statusCode()).isEqualTo(200);
		// outro paciente da mesma nutri, sem restrições: o mesmo alimento não gera alerta
		var sem=criarDieta(a.token(),q.fichaId(),dieta("Sem restrição",refeicao("Café",0,item(catupiry,"1"))));
		assertThat(sem.path("refeicoes").get(0).path("itens").get(0).has("alertas")).isFalse();
		assertThat(sem.has("restricoes")).isFalse();
	}
}
