package com.br.dietamais.metas;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.dietas.TesteComPaciente;
import java.util.*;
import org.junit.jupiter.api.Test;

/** BE-74b (H-04), RN-MET-04: modelos de metas da nutri, aplicação a um paciente e isolamento por nutri. */
class ModelosMetaApiTest extends TesteComPaciente {
	Map<String,Object> item(String tipo,Object alvo,String frequencia){
		var m=new HashMap<String,Object>();m.put("tipo",tipo);m.put("alvo",alvo);m.put("frequencia",frequencia);return m;
	}
	Map<String,Object> modelo(String nome,Map<String,Object>... itens){return Map.of("nome",nome,"itens",List.of(itens));}

	@Test void criaListaAtualizaEApagaComVersao() throws Exception {
		var a=cadastrarNutri();
		var criado=requisitar("POST","/eu/nutri/modelos-meta",a.token(),modelo("Rotina básica",item("SONO_HORAS",8,"DIARIA")));
		assertThat(criado.statusCode()).as(criado.body()).isEqualTo(201);
		assertThat(criado.headers().firstValue("ETag").orElseThrow()).isEqualTo("\"1\"");
		String id=json(criado).path("id").asString();
		assertThat(json(requisitar("GET","/eu/nutri/modelos-meta",a.token(),null)).path("itens")).hasSize(1);
		var semanal=item("ATIVIDADE_MIN",30,"SEMANAL");semanal.put("vezesPorSemana",3);
		var ok=requisitar("PUT","/eu/nutri/modelos-meta/"+id,a.token(),modelo("Rotina ativa",item("PASSOS",8000,"DIARIA"),semanal),Map.of("If-Match","\"1\""));
		assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);assertThat(json(ok).path("versao").asLong()).isEqualTo(2);assertThat(json(ok).path("itens")).hasSize(2);
		erro(requisitar("PUT","/eu/nutri/modelos-meta/"+id,a.token(),modelo("X",item("PASSOS",1,"DIARIA")),Map.of("If-Match","\"1\"")),409,"CONFLITO_VERSAO");
		erro(requisitar("DELETE","/eu/nutri/modelos-meta/"+id,a.token(),null,Map.of("If-Match","\"1\"")),409,"CONFLITO_VERSAO");
		assertThat(requisitar("DELETE","/eu/nutri/modelos-meta/"+id,a.token(),null,Map.of("If-Match","\"2\"")).statusCode()).isEqualTo(204);
		assertThat(json(requisitar("GET","/eu/nutri/modelos-meta",a.token(),null)).path("itens")).isEmpty();
	}

	@Test void rejeitaItemInvalidoEAgua() throws Exception {
		var a=cadastrarNutri();
		erro(requisitar("POST","/eu/nutri/modelos-meta",a.token(),modelo("Com água",item("AGUA",2000,"DIARIA"))),422,"META_INVALIDA");
		erro(requisitar("POST","/eu/nutri/modelos-meta",a.token(),modelo("Semanal sem N",item("PASSOS",8000,"SEMANAL"))),422,"META_INVALIDA");
		erro(requisitar("POST","/eu/nutri/modelos-meta",a.token(),modelo("Sim ou não sem nome",item("SIM_NAO",1,"DIARIA"))),422,"META_INVALIDA");
		erro(requisitar("POST","/eu/nutri/modelos-meta",a.token(),Map.of("nome","Vazio","itens",List.of())),400,"OBRIGATORIO");
	}

	@Test void aplicaAoPacienteEIsolaPorNutri() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();var p=pacienteVinculado(a);
		var sim=item("SIM_NAO",1,"DIARIA");sim.put("nome","Tomar o suplemento");
		String id=json(requisitar("POST","/eu/nutri/modelos-meta",a.token(),modelo("Rotina",item("PASSOS",8000,"DIARIA"),sim))).path("id").asString();
		var url="/pacientes/"+p.fichaId()+"/metas/de-modelo/"+id;
		var r=requisitar("POST",url,a.token(),null);assertThat(r.statusCode()).as(r.body()).isEqualTo(201);assertThat(json(r).path("itens")).hasSize(2);
		assertThat(json(requisitar("GET","/pacientes/"+p.fichaId()+"/metas",a.token(),null)).path("itens")).hasSize(2);
		// outra nutri: não vê, não altera, não aplica; o paciente não escreve
		erro(requisitar("PUT","/eu/nutri/modelos-meta/"+id,b.token(),modelo("X",item("PASSOS",1,"DIARIA")),Map.of("If-Match","\"1\"")),404,"RECURSO_NAO_ENCONTRADO");
		assertThat(json(requisitar("GET","/eu/nutri/modelos-meta",b.token(),null)).path("itens")).isEmpty();
		erro(requisitar("DELETE","/eu/nutri/modelos-meta/"+id,b.token(),null,Map.of("If-Match","\"1\"")),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("POST",url,b.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		assertThat(requisitar("POST",url,p.token(),null).statusCode()).isEqualTo(403);
		assertThat(requisitar("GET","/eu/nutri/modelos-meta",null,null).statusCode()).isEqualTo(401);
	}
}
