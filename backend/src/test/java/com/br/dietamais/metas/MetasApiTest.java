package com.br.dietamais.metas;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.dietas.TesteComPaciente;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** BE-74 (H-04), RN-MET-01 a 03: metas de hábito da nutri, registros do paciente, cumprimento e sync. */
class MetasApiTest extends TesteComPaciente {
	static final ZoneId FUSO=ZoneId.of("America/Sao_Paulo");
	LocalDate hoje(){return LocalDate.now(FUSO);}
	String novoId(){return jdbc.queryForObject("select uuidv7()",String.class);}
	Map<String,Object> meta(String tipo,Object alvo,String frequencia){
		var m=new HashMap<String,Object>();m.put("tipo",tipo);m.put("alvo",alvo);m.put("frequencia",frequencia);return m;
	}
	Map<String,Object> registro(String metaId,LocalDate data,Object valor){
		var m=new HashMap<String,Object>();m.put("metaId",metaId);m.put("data",data.toString());m.put("valor",valor);m.put("registradoEm",Instant.now().toString());return m;
	}
	String criar(String token,String ficha,Map<String,Object> corpo) throws Exception {
		var r=requisitar("POST","/pacientes/"+ficha+"/metas",token,corpo);assertThat(r.statusCode()).as(r.body()).isEqualTo(201);return json(r).path("id").asString();
	}

	@Test void validaTiposFrequenciaEPeriodo() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);String url="/pacientes/"+p.fichaId()+"/metas";
		var ok=requisitar("POST",url,a.token(),meta("PASSOS",8000,"DIARIA"));assertThat(ok.statusCode()).as(ok.body()).isEqualTo(201);
		assertThat(ok.headers().firstValue("ETag").orElseThrow()).isEqualTo("\"1\"");assertThat(json(ok).path("tipo").asString()).isEqualTo("PASSOS");
		var semanal=meta("ATIVIDADE_MIN",30,"SEMANAL");semanal.put("vezesPorSemana",3);assertThat(requisitar("POST",url,a.token(),semanal).statusCode()).isEqualTo(201);
		var sim=meta("SIM_NAO",1,"DIARIA");sim.put("nome","Tomar o suplemento");assertThat(requisitar("POST",url,a.token(),sim).statusCode()).isEqualTo(201);
		// META_INVALIDA: semanal sem N, diária com N, SIM_NAO sem nome ou alvo diferente de 1, nome em tipo fixo, água criada à mão, período invertido
		erro(requisitar("POST",url,a.token(),meta("PASSOS",8000,"SEMANAL")),422,"META_INVALIDA");
		var diariaComN=meta("PASSOS",8000,"DIARIA");diariaComN.put("vezesPorSemana",2);erro(requisitar("POST",url,a.token(),diariaComN),422,"META_INVALIDA");
		erro(requisitar("POST",url,a.token(),meta("SIM_NAO",1,"DIARIA")),422,"META_INVALIDA");
		var simAlvo=meta("SIM_NAO",2,"DIARIA");simAlvo.put("nome","X");erro(requisitar("POST",url,a.token(),simAlvo),422,"META_INVALIDA");
		var nomeFixo=meta("PASSOS",8000,"DIARIA");nomeFixo.put("nome","Caminhar");erro(requisitar("POST",url,a.token(),nomeFixo),422,"META_INVALIDA");
		erro(requisitar("POST",url,a.token(),meta("AGUA",2000,"DIARIA")),422,"META_INVALIDA");
		var invertido=meta("PASSOS",8000,"DIARIA");invertido.put("inicio","2026-12-10");invertido.put("fim","2026-12-01");erro(requisitar("POST",url,a.token(),invertido),422,"META_INVALIDA");
		erro(requisitar("POST",url,a.token(),meta("PASSOS",0,"DIARIA")),400,"FORMATO_INVALIDO");
		assertThat(json(requisitar("GET",url,a.token(),null)).path("itens")).hasSize(3);
	}

	@Test void ajustaEncerraComVersaoEIsolaDaOutraNutriEDoPaciente() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();var p=pacienteVinculado(a);
		String id=criar(a.token(),p.fichaId(),meta("PASSOS",8000,"DIARIA"));
		var ok=requisitar("PATCH","/metas/"+id,a.token(),Map.of("alvo",10000),Map.of("If-Match","\"1\""));
		assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);assertThat(json(ok).path("versao").asLong()).isEqualTo(2);assertThat(json(ok).path("alvo").decimalValue()).isEqualByComparingTo("10000");
		erro(requisitar("PATCH","/metas/"+id,a.token(),Map.of("alvo",9000),Map.of("If-Match","\"1\"")),409,"CONFLITO_VERSAO");
		erro(requisitar("PATCH","/metas/"+id,a.token(),Map.of("alvo",9000)),400,"FORMATO_INVALIDO");
		erro(requisitar("PATCH","/metas/"+id,a.token(),Map.of(),Map.of("If-Match","\"2\"")),400,"FORMATO_INVALIDO");
		erro(requisitar("PATCH","/metas/"+id,a.token(),Map.of("encerrar",true,"alvo",9000),Map.of("If-Match","\"2\"")),400,"FORMATO_INVALIDO");
		// outra nutri e o paciente não alteram nem veem
		erro(requisitar("PATCH","/metas/"+id,b.token(),Map.of("alvo",9000),Map.of("If-Match","\"2\"")),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("GET","/pacientes/"+p.fichaId()+"/metas",b.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("POST","/pacientes/"+p.fichaId()+"/metas",b.token(),meta("PASSOS",1000,"DIARIA")),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("GET","/pacientes/"+p.fichaId()+"/metas/cumprimento",b.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		assertThat(requisitar("PATCH","/metas/"+id,p.token(),Map.of("alvo",1),Map.of("If-Match","\"2\"")).statusCode()).isEqualTo(403);
		assertThat(requisitar("GET","/pacientes/"+p.fichaId()+"/metas",null,null).statusCode()).isEqualTo(401);
		// encerrar fecha a meta hoje
		var fim=requisitar("PATCH","/metas/"+id,a.token(),Map.of("encerrar",true),Map.of("If-Match","\"2\""));
		assertThat(fim.statusCode()).as(fim.body()).isEqualTo(200);assertThat(json(fim).path("fim").asString()).isNotBlank();
		// vínculo encerrado: só lê
		assertThat(requisitar("POST","/pacientes/"+p.fichaId()+"/encerrar-vinculo",a.token(),null).statusCode()).isEqualTo(200);
		erro(requisitar("POST","/pacientes/"+p.fichaId()+"/metas",a.token(),meta("PASSOS",1000,"DIARIA")),403,"ACESSO_NEGADO");
		erro(requisitar("PATCH","/metas/"+id,a.token(),Map.of("alvo",1),Map.of("If-Match","\"3\"")),403,"ACESSO_NEGADO");
		assertThat(requisitar("GET","/pacientes/"+p.fichaId()+"/metas",a.token(),null).statusCode()).isEqualTo(200);
	}

	@Test void pacienteRegistraIdempotenteComJanelaECumprimentoChegaANutri() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();var p=pacienteVinculado(a);var outro=pacienteVinculado(b);
		String passos=criar(a.token(),p.fichaId(),meta("PASSOS",8000,"DIARIA"));
		var sim=meta("SIM_NAO",1,"DIARIA");sim.put("nome","Alongar");String alongar=criar(a.token(),p.fichaId(),sim);
		var cursor=json(requisitar("GET","/sync?limite=1000",p.token(),null)).path("cursor").asString();
		var corpo=registro(passos,hoje(),5000);corpo.put("id",novoId());
		var r1=requisitar("POST","/eu/registros-meta",p.token(),corpo);assertThat(r1.statusCode()).as(r1.body()).isEqualTo(201);
		var r2=requisitar("POST","/eu/registros-meta",p.token(),corpo);assertThat(r2.statusCode()).isEqualTo(201);assertThat(json(r2)).isEqualTo(json(r1));
		// registros do mesmo dia somam: 5000 + 3500 >= 8000
		assertThat(requisitar("POST","/eu/registros-meta",p.token(),registro(passos,hoje(),3500)).statusCode()).isEqualTo(201);
		assertThat(requisitar("POST","/eu/registros-meta",p.token(),registro(alongar,hoje(),1)).statusCode()).isEqualTo(201);
		// regras de registro
		erro(requisitar("POST","/eu/registros-meta",p.token(),registro(alongar,hoje(),2)),400,"FORMATO_INVALIDO");
		erro(requisitar("POST","/eu/registros-meta",p.token(),registro(passos,hoje().minusDays(4),100)),400,"FORMATO_INVALIDO");
		erro(requisitar("POST","/eu/registros-meta",p.token(),registro(passos,hoje().plusDays(2),100)),400,"FORMATO_INVALIDO");
		erro(requisitar("POST","/eu/registros-meta",p.token(),registro(passos,hoje(),-1)),400,"FORMATO_INVALIDO");
		// meta de outro paciente não existe para este; perfil da nutri não registra; id de outro paciente não é tomado
		erro(requisitar("POST","/eu/registros-meta",outro.token(),registro(passos,hoje(),100)),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("POST","/eu/registros-meta",a.token(),registro(passos,hoje(),100)),403,"PERFIL_INVALIDO");
		var roubo=registro(criar(b.token(),outro.fichaId(),meta("PASSOS",8000,"DIARIA")),hoje(),100);roubo.put("id",corpo.get("id"));
		erro(requisitar("POST","/eu/registros-meta",outro.token(),roubo),409,"CONFLITO_VERSAO");
		assertThat(requisitar("POST","/eu/registros-meta",null,corpo).statusCode()).isEqualTo(401);
		// cumprimento da nutri: hoje cumpriu passos (1 de 1 previsto no dia aberto) e alongar
		var c=json(requisitar("GET","/pacientes/"+p.fichaId()+"/metas/cumprimento?de="+hoje()+"&ate="+hoje(),a.token(),null));
		assertThat(c.path("metas")).hasSize(2);
		for(JsonNode m:c.path("metas")){assertThat(m.path("diasPrevistos").asInt()).isEqualTo(1);assertThat(m.path("diasCumpridos").asInt()).isEqualTo(1);assertThat(m.path("percentual").decimalValue()).isEqualByComparingTo("100");}
		erro(requisitar("GET","/pacientes/"+p.fichaId()+"/metas/cumprimento?de="+hoje()+"&ate="+hoje().minusDays(1),a.token(),null),400,"FORMATO_INVALIDO");
		erro(requisitar("GET","/pacientes/"+p.fichaId()+"/metas/cumprimento?de="+hoje().minusDays(400)+"&ate="+hoje(),a.token(),null),400,"FORMATO_INVALIDO");
		// sync: o paciente recebe as metas e os próprios registros; a nutri do vínculo também recebe os registros; a outra nutri nada
		var viaSync=json(requisitar("GET","/sync?limite=1000&cursor="+cursor,p.token(),null)).path("mudancas");
		assertThat(viaSync.path("registrosMeta")).hasSize(3);
		assertThat(json(requisitar("GET","/sync?limite=1000",p.token(),null)).path("mudancas").path("metas")).hasSize(2);
		var nutri=json(requisitar("GET","/sync?limite=1000",a.token(),null)).path("mudancas");assertThat(nutri.path("metas")).hasSize(2);assertThat(nutri.path("registrosMeta")).hasSize(3);
		assertThat(json(requisitar("GET","/sync?limite=1000",b.token(),null)).path("mudancas").path("registrosMeta")).hasSize(1); // só o do paciente dela
		// mutação do sync: só CRIAR, com as mesmas regras
		var lote=Map.of("mutacoes",List.of(Map.of("idMutacao",UUID.randomUUID().toString(),"entidade","registrosMeta","operacao","CRIAR","id",novoId(),"dados",registro(passos,hoje().minusDays(1),9000))));
		var rs=requisitar("POST","/sync",p.token(),lote);assertThat(rs.statusCode()).as(rs.body()).isEqualTo(200);assertThat(json(rs).path("resultados").get(0).path("status").asString()).isEqualTo("APLICADA");
	}
}
