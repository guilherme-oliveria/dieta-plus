package com.br.dietamais.metas;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.dietas.TesteComPaciente;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** BE-86 (D-07), RN-MET-05 e RN-ROT-03: a água é a meta AGUA de cada vínculo, com o caminho antigo ainda aceito e sem regressão nos totais. */
class AguaComoMetaApiTest extends TesteComPaciente {
	static final ZoneId FUSO=ZoneId.of("America/Sao_Paulo");
	LocalDate hoje(){return LocalDate.now(FUSO);}
	String novoId(){return jdbc.queryForObject("select uuidv7()",String.class);}
	JsonNode metaAgua(ContaTeste nutri,PacienteTeste p) throws Exception {
		JsonNode achada=null;int n=0;
		for(var m:json(requisitar("GET","/pacientes/"+p.fichaId()+"/metas",nutri.token(),null)).path("itens"))if(m.path("tipo").asString().equals("AGUA")){achada=m;n++;}
		assertThat(n).as("uma meta de água por vínculo").isEqualTo(1);return achada;
	}
	Map<String,Object> registro(String metaId,Object valor){
		var m=new HashMap<String,Object>();m.put("id",novoId());m.put("metaId",metaId);m.put("data",hoje().toString());m.put("valor",valor);m.put("registradoEm",Instant.now().toString());return m;
	}
	long totalMeta(String metaId){return jdbc.queryForObject("select coalesce(sum(valor),0)::bigint from registro_meta where meta_id=?::uuid",Long.class,metaId);}
	long totalAgua(String pacienteId){return jdbc.queryForObject("select coalesce(sum(ml),0)::bigint from registro_agua where paciente_id=?::uuid",Long.class,pacienteId);}
	Map<String,Object> agua(int ml){return Map.of("id",novoId(),"ml",ml,"registradoEm",Instant.now().toString());}

	@Test void nasceComOVinculoComAlvoPadraoQueSegueOPesoAteANutriAjustar() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);
		var m=metaAgua(a,p);String id=m.path("id").asString();
		// sem peso: 2.000 ml; diária, sem nome nem unidade, alvoPadrao=true
		assertThat(m.path("alvo").decimalValue()).isEqualByComparingTo("2000");assertThat(m.path("alvoPadrao").asBoolean()).isTrue();
		assertThat(m.path("frequencia").asString()).isEqualTo("DIARIA");assertThat(m.path("nome").isMissingNode()).isTrue();
		// peso novo: 35 ml × 70 kg
		assertThat(requisitar("POST","/eu/pesos",p.token(),Map.of("pesoKg",70,"registradoEm",Instant.now().toString())).statusCode()).isEqualTo(201);
		m=metaAgua(a,p);assertThat(m.path("alvo").decimalValue()).isEqualByComparingTo("2450");assertThat(m.path("versao").asLong()).isEqualTo(2);
		// o paciente recebe a meta pelo sync
		var doPaciente=json(requisitar("GET","/sync?limite=1000",p.token(),null)).path("mudancas").path("metas");
		assertThat(doPaciente).hasSize(1);assertThat(doPaciente.get(0).path("dados").path("alvo").decimalValue()).isEqualByComparingTo("2450");
		// a nutri ajusta: o alvo dela vale e o peso não muda mais o alvo
		var ajuste=requisitar("PATCH","/metas/"+id,a.token(),Map.of("alvo",3000),Map.of("If-Match","\"2\""));
		assertThat(ajuste.statusCode()).as(ajuste.body()).isEqualTo(200);assertThat(json(ajuste).path("alvoPadrao").asBoolean()).isFalse();
		assertThat(requisitar("POST","/eu/pesos",p.token(),Map.of("pesoKg",80,"registradoEm",Instant.now().toString())).statusCode()).isEqualTo(201);
		m=metaAgua(a,p);assertThat(m.path("alvo").decimalValue()).isEqualByComparingTo("3000");assertThat(m.path("versao").asLong()).isEqualTo(3);
		// frequência ajusta como nas outras metas; não se encerra nem se cria outra
		var semanal=requisitar("PATCH","/metas/"+id,a.token(),Map.of("frequencia","SEMANAL","vezesPorSemana",5),Map.of("If-Match","\"3\""));
		assertThat(semanal.statusCode()).as(semanal.body()).isEqualTo(200);assertThat(json(semanal).path("alvo").decimalValue()).isEqualByComparingTo("3000");
		erro(requisitar("PATCH","/metas/"+id,a.token(),Map.of("encerrar",true),Map.of("If-Match","\"4\"")),422,"META_INVALIDA");
		erro(requisitar("POST","/pacientes/"+p.fichaId()+"/metas",a.token(),Map.of("tipo","AGUA","alvo",2000,"frequencia","DIARIA")),422,"META_INVALIDA");
		// isolamento: outra nutri não vê nem altera
		var b=cadastrarNutri();
		erro(requisitar("PATCH","/metas/"+id,b.token(),Map.of("alvo",1000),Map.of("If-Match","\"4\"")),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("GET","/pacientes/"+p.fichaId()+"/metas",b.token(),null),404,"RECURSO_NAO_ENCONTRADO");
	}

	@Test void alvoPadraoUsaOPesoMaisRecente() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);
		jdbc.update("insert into registro_peso(id,paciente_id,peso_kg,registrado_em) values(uuidv7(),?::uuid,60,now()-interval '1 day')",p.pacienteId());
		assertThat(metaAgua(a,p).path("alvo").decimalValue()).isEqualByComparingTo("2100");
		// um peso mais antigo, lançado depois, não muda o alvo
		jdbc.update("insert into registro_peso(id,paciente_id,peso_kg,registrado_em) values(uuidv7(),?::uuid,90,now()-interval '30 days')",p.pacienteId());
		assertThat(metaAgua(a,p).path("alvo").decimalValue()).isEqualByComparingTo("2100");
		assertThat(jdbc.queryForObject("select alvo_agua_padrao(?::uuid)::int",Integer.class,p.pacienteId())).isEqualTo(2100);
	}

	@Test void caminhoAntigoEONovoSomamOMesmoTotalSemDuplicar() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);String meta=metaAgua(a,p).path("id").asString();
		// app 1.3.x: REST e sync
		var antigo=agua(300);
		assertThat(requisitar("POST","/eu/agua",p.token(),antigo).statusCode()).isEqualTo(201);
		assertThat(requisitar("POST","/eu/agua",p.token(),antigo).statusCode()).isEqualTo(201); // mesmo id: nada duplica
		var lote=Map.of("mutacoes",List.of(Map.of("idMutacao",UUID.randomUUID().toString(),"entidade","agua","operacao","CRIAR","id",novoId(),"dados",Map.of("ml",200,"registradoEm",Instant.now().toString()))));
		var rs=requisitar("POST","/sync",p.token(),lote);assertThat(rs.statusCode()).as(rs.body()).isEqualTo(200);
		assertThat(totalMeta(meta)).isEqualTo(500);
		// app 1.4.0: registro na meta, que também chega à rotina antiga
		var novo=registro(meta,500);
		var r1=requisitar("POST","/eu/registros-meta",p.token(),novo);assertThat(r1.statusCode()).as(r1.body()).isEqualTo(201);
		assertThat(requisitar("POST","/eu/registros-meta",p.token(),novo).statusCode()).isEqualTo(201);
		assertThat(totalMeta(meta)).isEqualTo(1000);assertThat(totalAgua(p.pacienteId())).isEqualTo(1000);
		var rotina=json(requisitar("GET","/eu/rotina?de="+hoje()+"&ate="+hoje(),p.token(),null));
		long soma=0;for(var x:rotina.path("agua"))soma+=x.path("ml").asLong();assertThat(soma).isEqualTo(1000);
		// água em ml inteiros de 1 a 5.000, como no caminho antigo
		erro(requisitar("POST","/eu/registros-meta",p.token(),registro(meta,2.5)),400,"FORMATO_INVALIDO");
		erro(requisitar("POST","/eu/registros-meta",p.token(),registro(meta,5001)),400,"FORMATO_INVALIDO");
		erro(requisitar("POST","/eu/registros-meta",p.token(),registro(meta,0)),400,"FORMATO_INVALIDO");
		// cumprimento: 1.000 de 2.000 hoje, ainda não cumpriu
		var c=json(requisitar("GET","/pacientes/"+p.fichaId()+"/metas/cumprimento?de="+hoje()+"&ate="+hoje(),a.token(),null)).path("metas");
		assertThat(c).hasSize(1);assertThat(c.get(0).path("diasCumpridos").asInt()).isZero();
		assertThat(requisitar("POST","/eu/agua",p.token(),agua(1000)).statusCode()).isEqualTo(201);
		c=json(requisitar("GET","/pacientes/"+p.fichaId()+"/metas/cumprimento?de="+hoje()+"&ate="+hoje(),a.token(),null)).path("metas");
		assertThat(c.get(0).path("diasCumpridos").asInt()).isEqualTo(1);
		// a nutri recebe os registros da meta pelo sync; outra nutri, nada
		assertThat(json(requisitar("GET","/sync?limite=1000",a.token(),null)).path("mudancas").path("registrosMeta")).hasSize(4);
		var b=cadastrarNutri();pacienteVinculado(b);
		assertThat(json(requisitar("GET","/sync?limite=1000",b.token(),null)).path("mudancas").path("registrosMeta")).isEmpty();
	}

	@Test void migracaoDaBase131MantemOsTotaisESePodeRepetir() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);
		for(int ml:new int[]{250,400,1200})assertThat(requisitar("POST","/eu/agua",p.token(),agua(ml)).statusCode()).isEqualTo(201);
		// base como na 1.3.1: só registro_agua, sem meta de água
		jdbc.update("delete from registro_meta where paciente_id=?::uuid",p.pacienteId());
		jdbc.update("delete from meta_habito where paciente_id=?::uuid and tipo='AGUA'",p.pacienteId());
		var de=hoje().minusDays(7);var antes=json(requisitar("GET","/eu/rotina?de="+de+"&ate="+hoje(),p.token(),null)).path("agua");
		var adesaoAntes=json(requisitar("GET","/pacientes/"+p.fichaId()+"/adesao",a.token(),null));
		assertThat(totalAgua(p.pacienteId())).isEqualTo(1850);
		jdbc.queryForObject("select migrar_agua_para_metas()",Integer.class);
		String meta=metaAgua(a,p).path("id").asString();
		assertThat(totalMeta(meta)).isEqualTo(1850);
		assertThat(jdbc.queryForObject("select count(*) from registro_meta where meta_id=?::uuid",Integer.class,meta)).isEqualTo(3);
		// de novo: nada muda
		jdbc.queryForObject("select migrar_agua_para_metas()",Integer.class);
		assertThat(jdbc.queryForObject("select count(*) from registro_meta where meta_id=?::uuid",Integer.class,meta)).isEqualTo(3);
		// rotina, adesão e tabela antiga sem regressão
		assertThat(json(requisitar("GET","/eu/rotina?de="+de+"&ate="+hoje(),p.token(),null)).path("agua")).isEqualTo(antes);
		assertThat(json(requisitar("GET","/pacientes/"+p.fichaId()+"/adesao",a.token(),null))).isEqualTo(adesaoAntes);
		assertThat(totalAgua(p.pacienteId())).isEqualTo(1850);
	}

	@Test void vinculoEncerradoNaoRecebeAguaNovaENovoVinculoGanhaAMeta() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);String meta=metaAgua(a,p).path("id").asString();
		assertThat(requisitar("POST","/eu/agua",p.token(),agua(300)).statusCode()).isEqualTo(201);
		assertThat(requisitar("POST","/pacientes/"+p.fichaId()+"/encerrar-vinculo",a.token(),null).statusCode()).isEqualTo(200);
		assertThat(requisitar("POST","/eu/agua",p.token(),agua(700)).statusCode()).isEqualTo(201);
		assertThat(totalMeta(meta)).isEqualTo(300); // a nutri do vínculo encerrado não recebe o que veio depois
		assertThat(totalAgua(p.pacienteId())).isEqualTo(1000);
	}
}
