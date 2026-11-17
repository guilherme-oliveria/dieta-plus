package com.br.dietamais.rotina;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.dietas.TesteComPaciente;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** BE-73 (H-03), RN-ROT-01 e RN-ROT-06: fome antes, sensação depois e "comi fora do plano". */
class DiarioHumorApiTest extends TesteComPaciente {
	static final ZoneId FUSO=ZoneId.of("America/Sao_Paulo");
	LocalDate hoje(){return LocalDate.now(FUSO);}
	String novoId(){return jdbc.queryForObject("select uuidv7()",String.class);}
	Map<String,Object> checkin(JsonNode s,Object fome,Object sensacao){
		var m=new HashMap<String,Object>();m.put("dietaId",s.path("dietaId").asString());m.put("refeicaoId",s.path("refeicoes").get(0).path("id").asString());
		m.put("data",hoje().toString());m.put("status","FEITO");m.put("registradoEm",Instant.now().toString());
		if(fome!=null)m.put("fomeAntes",fome);if(sensacao!=null)m.put("sensacaoDepois",sensacao);return m;
	}
	Map<String,Object> fora(String texto){
		var m=new HashMap<String,Object>();m.put("data",hoje().toString());m.put("texto",texto);m.put("registradoEm",Instant.now().toString());return m;
	}

	@Test void checkinGuardaHumorEValidaIntervalo() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);var ali=alimentos(a.token(),1);
		var s=publicar(a.token(),criarDieta(a.token(),p.fichaId(),dieta("Humor",refeicao("Café",0,item(ali.get(0),"1")))));
		var ok=requisitar("POST","/eu/checkins",p.token(),checkin(s,2,4));assertThat(ok.statusCode()).as(ok.body()).isEqualTo(201);
		assertThat(json(ok).path("fomeAntes").asInt()).isEqualTo(2);assertThat(json(ok).path("sensacaoDepois").asInt()).isEqualTo(4);
		var sem=requisitar("POST","/eu/checkins",p.token(),checkin(s,null,null));assertThat(sem.statusCode()).isEqualTo(201);assertThat(json(sem).has("fomeAntes")).isFalse();
		erro(requisitar("POST","/eu/checkins",p.token(),checkin(s,0,3)),400,"FORMATO_INVALIDO");
		erro(requisitar("POST","/eu/checkins",p.token(),checkin(s,3,6)),400,"FORMATO_INVALIDO");
		// a nutri vê as médias da semana na adesão
		var adesao=json(requisitar("GET","/pacientes/"+p.fichaId()+"/adesao",a.token(),null));
		assertThat(adesao.path("humor").path("registros").asInt()).isEqualTo(1);
		assertThat(adesao.path("humor").path("mediaFomeAntes").decimalValue()).isEqualByComparingTo("2.0");
		assertThat(adesao.path("humor").path("mediaSensacaoDepois").decimalValue()).isEqualByComparingTo("4.0");
	}

	@Test void foraDoPlanoSoInclusaoIsolaEChegaAoSync() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);var b=cadastrarNutri();
		var cursor=json(requisitar("GET","/sync?limite=1000",p.token(),null)).path("cursor").asString();
		var corpo=fora("  Brigadeiro na festa  ");corpo.put("id",novoId());
		var r1=requisitar("POST","/eu/registros-fora-do-plano",p.token(),corpo);assertThat(r1.statusCode()).as(r1.body()).isEqualTo(201);
		assertThat(json(r1).path("texto").asString()).isEqualTo("Brigadeiro na festa");
		var r2=requisitar("POST","/eu/registros-fora-do-plano",p.token(),corpo);assertThat(r2.statusCode()).isEqualTo(201);assertThat(json(r2)).isEqualTo(json(r1));
		assertThat(jdbc.queryForObject("select count(*) from registro_fora_do_plano",Integer.class)).isEqualTo(1);
		// validações: texto em branco ou longo, data fora da janela, perfil e autenticação
		erro(requisitar("POST","/eu/registros-fora-do-plano",p.token(),fora(" ")),400,"OBRIGATORIO");
		erro(requisitar("POST","/eu/registros-fora-do-plano",p.token(),fora("x".repeat(281))),400,"FORMATO_INVALIDO");
		var antiga=fora("ontem");antiga.put("data",hoje().minusDays(4).toString());erro(requisitar("POST","/eu/registros-fora-do-plano",p.token(),antiga),400,"FORMATO_INVALIDO");
		erro(requisitar("POST","/eu/registros-fora-do-plano",a.token(),fora("x")),403,"PERFIL_INVALIDO");
		assertThat(requisitar("POST","/eu/registros-fora-do-plano",null,fora("x")).statusCode()).isEqualTo(401);
		// o id de outro paciente não é tomado
		var outro=pacienteVinculado(b);erro(requisitar("POST","/eu/registros-fora-do-plano",outro.token(),corpo),409,"CONFLITO_VERSAO");
		// sync: o paciente e a nutri do vínculo recebem; a outra nutri não
		assertThat(json(requisitar("GET","/sync?limite=1000&cursor="+cursor,p.token(),null)).path("mudancas").path("registrosForaDoPlano")).hasSize(1);
		assertThat(json(requisitar("GET","/sync?limite=1000",a.token(),null)).path("mudancas").path("registrosForaDoPlano")).hasSize(1);
		assertThat(json(requisitar("GET","/sync?limite=1000",b.token(),null)).path("mudancas").path("registrosForaDoPlano")).isEmpty();
		// mutação do sync: só CRIAR, com as mesmas regras
		var m=Map.of("mutacoes",List.of(Map.of("idMutacao",UUID.randomUUID().toString(),"entidade","registrosForaDoPlano","operacao","CRIAR","id",novoId(),"dados",fora("pizza"))));
		var viaSync=requisitar("POST","/sync",p.token(),m);assertThat(viaSync.statusCode()).as(viaSync.body()).isEqualTo(200);
		assertThat(json(viaSync).path("resultados").get(0).path("status").asString()).isEqualTo("APLICADA");
		// adesão da nutri traz os registros; GET /eu/rotina também
		var adesao=json(requisitar("GET","/pacientes/"+p.fichaId()+"/adesao",a.token(),null));assertThat(adesao.path("foraDoPlano")).hasSize(2);
		erro(requisitar("GET","/pacientes/"+p.fichaId()+"/adesao",b.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		var rotina=json(requisitar("GET","/eu/rotina?de="+hoje()+"&ate="+hoje(),p.token(),null));assertThat(rotina.path("registrosForaDoPlano")).hasSize(2);
	}
}
