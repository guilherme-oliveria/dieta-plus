package com.br.dietamais.dietas;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.comum.TesteIsolamento;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** BE-83 (MOB-29b): modelos de dieta da nutri escritos pelo POST /sync com CRIAR, ATUALIZAR e EXCLUIR, com as mesmas regras do REST (RN-MOD-01). */
class SyncModelosDietaApiTest extends TesteIsolamento {
	String id(){return jdbc.queryForObject("select uuidv7()",String.class);}
	Map<String,Object> modelo(String titulo){
		var item=new HashMap<String,Object>();item.put("aVontade",true);item.put("textoLivre","Folhas à vontade");
		var refeicao=new HashMap<String,Object>();refeicao.put("id",id());refeicao.put("nome","Almoço");refeicao.put("horario","12:00:00");refeicao.put("ordem",0);refeicao.put("itens",new ArrayList<Object>(List.of(item)));
		var m=new HashMap<String,Object>();m.put("titulo",titulo);m.put("refeicoes",new ArrayList<Object>(List.of(refeicao)));return m;
	}
	JsonNode enviar(String token,String operacao,String id,Object versaoBase,Object dados) throws Exception {
		var mut=new HashMap<String,Object>();mut.put("idMutacao",UUID.randomUUID().toString());mut.put("entidade","modelosDieta");mut.put("operacao",operacao);mut.put("id",id);mut.put("versaoBase",versaoBase);mut.put("dados",dados);
		var r=requisitar("POST","/sync",token,Map.of("mutacoes",List.of(mut)));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json(r).path("resultados").get(0);
	}
	@Test void nutriCriaAtualizaEExcluiModeloPeloSyncComConflitoPorVersaoBase() throws Exception {
		var a=cadastrarNutri();String id=id();
		var criado=enviar(a.token(),"CRIAR",id,null,modelo("Modelo offline"));assertThat(criado.path("status").asString()).as(criado.toString()).isEqualTo("APLICADA");
		assertThat(criado.path("versao").asLong()).isEqualTo(1);
		assertThat(json(requisitar("GET","/eu/nutri/modelos-dieta/"+id,a.token(),null)).path("titulo").asString()).isEqualTo("Modelo offline");
		var editado=enviar(a.token(),"ATUALIZAR",id,1,modelo("Modelo editado"));assertThat(editado.path("status").asString()).as(editado.toString()).isEqualTo("APLICADA");
		assertThat(editado.path("versao").asLong()).isEqualTo(2);
		// versão velha: conflito com o registro atual, nada gravado
		var conflito=enviar(a.token(),"ATUALIZAR",id,1,modelo("Outro"));assertThat(conflito.path("status").asString()).isEqualTo("CONFLITO");
		assertThat(conflito.path("atual").path("titulo").asString()).isEqualTo("Modelo editado");
		// ATUALIZAR e EXCLUIR exigem versaoBase
		assertThat(enviar(a.token(),"EXCLUIR",id,null,Map.of()).path("status").asString()).isEqualTo("REJEITADA");
		var excluido=enviar(a.token(),"EXCLUIR",id,2,Map.of());assertThat(excluido.path("status").asString()).as(excluido.toString()).isEqualTo("APLICADA");
		assertThat(excluido.path("versao").asLong()).isEqualTo(3);
		erro(requisitar("GET","/eu/nutri/modelos-dieta/"+id,a.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		// a exclusão vira tombstone no sync da nutri
		var mudancas=json(requisitar("GET","/sync?limite=1000",a.token(),null)).path("mudancas").path("modelosDieta");
		assertThat(mudancas.toString()).contains(id);
	}
	@Test void modeloDeOutraNutriEDoSistemaNaoSeMexePeloSync() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();String id=id();
		enviar(a.token(),"CRIAR",id,null,modelo("Da Ana"));
		var roubo=enviar(b.token(),"ATUALIZAR",id,1,modelo("Roubado"));assertThat(roubo.path("status").asString()).isEqualTo("REJEITADA");assertThat(roubo.path("codigo").asString()).isEqualTo("RECURSO_NAO_ENCONTRADO");
		var exclusao=enviar(b.token(),"EXCLUIR",id,1,Map.of());assertThat(exclusao.path("codigo").asString()).isEqualTo("RECURSO_NAO_ENCONTRADO");
		assertThat(json(requisitar("GET","/eu/nutri/modelos-dieta/"+id,a.token(),null)).path("titulo").asString()).isEqualTo("Da Ana");
		// modelo do sistema: somente leitura
		String sistema=id();jdbc.update("insert into modelo_dieta(id,nutricionista_id,titulo,refeicoes,metas,kcal_referencia) values(?::uuid,null,'Modelo fictício do sistema','[]'::jsonb,null,null)",sistema);
		var edicao=enviar(a.token(),"ATUALIZAR",sistema,1,modelo("Mexido"));assertThat(edicao.path("status").asString()).isEqualTo("REJEITADA");assertThat(edicao.path("codigo").asString()).isEqualTo("ACESSO_NEGADO");
		assertThat(enviar(a.token(),"EXCLUIR",sistema,1,Map.of()).path("status").asString()).isEqualTo("REJEITADA");
		assertThat(jdbc.queryForObject("select titulo from modelo_dieta where id=?::uuid",String.class,sistema)).isEqualTo("Modelo fictício do sistema");
	}
}
