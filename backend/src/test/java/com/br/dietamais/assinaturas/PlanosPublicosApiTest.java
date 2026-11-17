package com.br.dietamais.assinaturas;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.dietas.TesteComPaciente;
import java.util.*;
import org.junit.jupiter.api.Test;

/** BE-80 (H-07), RN-ASS-01, 02 e 09: planos à venda, endpoint público, níveis novos e campos novos de `GET /assinatura`. */
class PlanosPublicosApiTest extends TesteComPaciente {
	String admin() throws Exception {
		String email="admin-"+UUID.randomUUID()+"@example.invalid";
		jdbc.update("insert into usuario(email,senha_hash,nome,perfil,ativo,email_confirmado_em) select ?,senha_hash,'Admin fictício','ADMIN',true,now() from usuario where id=?",email,cadastrarNutri().usuario());
		return entrar(email,null);
	}
	void restaurar(){jdbc.update("update plano set ativo=false,a_venda=false,preco_mensal=0,preco_anual=null where codigo in ('CONSULTORIO','PLUS')");}

	@Test void publicoListaSoPlanosAtivosEAVendaSemLogin() throws Exception {
		var r=requisitar("GET","/planos",null,null);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
		var itens=json(r).path("itens");assertThat(itens).hasSize(1);
		assertThat(itens.get(0).path("codigo").asString()).isEqualTo("BASICO_20");
		assertThat(itens.get(0).path("precoAnual").decimalValue()).isEqualByComparingTo("299");assertThat(itens.get(0).path("limitePacientes").asInt()).isEqualTo(20);
		assertThat(r.body()).doesNotContain("CONSULTORIO","PLUS","TESTE");
	}

	@Test void adminAtivaNiveisNovosEOPublicoPassaAVer() throws Exception {
		String t=admin();
		try{
			var lista=json(requisitar("GET","/admin/planos",t,null));assertThat(lista.toString()).contains("CONSULTORIO","PLUS");
			String consultorio=jdbc.queryForObject("select id::text from plano where codigo='CONSULTORIO'",String.class);
			String plus=jdbc.queryForObject("select id::text from plano where codigo='PLUS'",String.class);
			// à venda exige preço
			erro(requisitar("PUT","/admin/planos/"+consultorio,t,Map.of("nome","Consultório","precoMensal",0,"limitePacientes",80,"ativo",true,"aVenda",true,"motivo","Teste")),400,"FORMATO_INVALIDO");
			var ok=requisitar("PUT","/admin/planos/"+consultorio,t,Map.of("nome","Consultório","precoMensal",79.9,"precoAnual",799,"limitePacientes",80,"ativo",true,"aVenda",true,"motivo","Preço decidido"));
			assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);assertThat(json(ok).path("aVenda").asBoolean()).isTrue();
			var mudo=new HashMap<String,Object>(Map.of("nome","Plus","precoMensal",179.9,"precoAnual",1799,"ativo",true,"aVenda",true,"motivo","Preço decidido"));
			var plusOk=requisitar("PUT","/admin/planos/"+plus,t,mudo);assertThat(plusOk.statusCode()).as(plusOk.body()).isEqualTo(200);
			assertThat(json(plusOk).path("limitePacientes").isNull()).isTrue();
			var itens=json(requisitar("GET","/planos",null,null)).path("itens");
			assertThat(itens).hasSize(3);assertThat(itens.get(1).path("codigo").asString()).isEqualTo("CONSULTORIO");
			assertThat(itens.get(2).path("codigo").asString()).isEqualTo("PLUS");assertThat(itens.get(2).path("limitePacientes").isNull()).isTrue();
			// a nutri vê os mesmos planos em GET /assinatura
			var a=cadastrarNutri();var d=json(requisitar("GET","/assinatura",a.token(),null));
			assertThat(d.path("planosDisponiveis")).hasSize(3);
			assertThat(d.path("periodicidade").asString()).isEqualTo("MENSAL");assertThat(d.path("exibePrecoEBotao").asBoolean()).isTrue();
			assertThat(d.path("diasRestantesTeste").asInt()).isBetween(1,60);
		}finally{restaurar();}
	}

	@Test void plusSemLimiteNaoBarraPacientes() throws Exception {
		try{
			var a=cadastrarNutri();
			jdbc.update("update plano set ativo=true,a_venda=true,preco_mensal=179.9,limite_pacientes=null where codigo='PLUS'");
			jdbc.update("update assinatura set status='ATIVA',plano_id=(select id from plano where codigo='PLUS'),fim_periodo_em=now()+interval '30 days' where organizacao_id=?",a.organizacao());
			var d=json(requisitar("GET","/assinatura",a.token(),null));assertThat(d.path("plano").path("codigo").asString()).isEqualTo("PLUS");
			assertThat(d.path("plano").path("limitePacientes").isNull()).isTrue();
			assertThat(requisitar("POST","/pacientes",a.token(),Map.of("nome","Paciente fictício","email","p-"+UUID.randomUUID()+"@example.invalid")).statusCode()).isEqualTo(201);
		}finally{restaurar();}
	}
}
