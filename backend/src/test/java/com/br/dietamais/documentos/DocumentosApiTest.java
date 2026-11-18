package com.br.dietamais.documentos;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.dietas.TesteComPaciente;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** BE-78 (H-06), RN-DOC-01 e 02: modelos do sistema só leitura, modelos da nutri, emissão imutável com PDF, declaração a partir de consulta, anulação, auditoria e isolamento. */
class DocumentosApiTest extends TesteComPaciente {
	static final String ATESTADO="019a9000-0000-7000-8000-00000000d001";
	static final String DECLARACAO="019a9000-0000-7000-8000-00000000d002";

	@Test void modelosDoSistemaSaoSoLeituraEANutriCopia() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();
		var lista=json(requisitar("GET","/eu/nutri/modelos-documento",a.token(),null)).path("itens");
		assertThat(lista).hasSize(2);
		assertThat(lista.get(0).path("doSistema").asBoolean()).isTrue();assertThat(lista.get(1).path("doSistema").asBoolean()).isTrue();
		var tipos=new HashSet<String>();lista.forEach(m->tipos.add(m.path("tipo").asString()));assertThat(tipos).containsExactlyInAnyOrder("ATESTADO","DECLARACAO_COMPARECIMENTO");
		// RN-DOC-01: modelo do sistema não muda
		erro(requisitar("PUT","/eu/nutri/modelos-documento/"+ATESTADO,a.token(),Map.of("tipo","ATESTADO","nome","X","texto","Y"),Map.of("If-Match","\"1\"")),403,"ACESSO_NEGADO");
		erro(requisitar("DELETE","/eu/nutri/modelos-documento/"+DECLARACAO,a.token(),null,Map.of("If-Match","\"1\"")),403,"ACESSO_NEGADO");
		// a nutri copia o texto do sistema e edita a cópia
		String textoSistema=lista.get(0).path("texto").asString();
		var copia=requisitar("POST","/eu/nutri/modelos-documento",a.token(),Map.of("tipo",lista.get(0).path("tipo").asString(),"nome","Minha cópia","texto",textoSistema+" Com carinho."));
		assertThat(copia.statusCode()).as(copia.body()).isEqualTo(201);assertThat(copia.headers().firstValue("ETag").orElseThrow()).isEqualTo("\"1\"");
		var m=json(copia);String id=m.path("id").asString();assertThat(m.path("doSistema").asBoolean()).isFalse();
		assertThat(json(requisitar("GET","/eu/nutri/modelos-documento",a.token(),null)).path("itens")).hasSize(3);
		assertThat(json(requisitar("GET","/eu/nutri/modelos-documento",b.token(),null)).path("itens")).hasSize(2);
		// isolamento: de outra nutri é 404
		erro(requisitar("PUT","/eu/nutri/modelos-documento/"+id,b.token(),Map.of("tipo","ATESTADO","nome","X","texto","Y"),Map.of("If-Match","\"1\"")),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("DELETE","/eu/nutri/modelos-documento/"+id,b.token(),null,Map.of("If-Match","\"1\"")),404,"RECURSO_NAO_ENCONTRADO");
		var ok=requisitar("PUT","/eu/nutri/modelos-documento/"+id,a.token(),Map.of("tipo","ATESTADO","nome","Atestado 2","texto","Atesto que {{paciente}} veio."),Map.of("If-Match","\"1\""));
		assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);assertThat(json(ok).path("versao").asLong()).isEqualTo(2);
		erro(requisitar("PUT","/eu/nutri/modelos-documento/"+id,a.token(),Map.of("tipo","ATESTADO","nome","X","texto","Y"),Map.of("If-Match","\"1\"")),409,"CONFLITO_VERSAO");
		erro(requisitar("POST","/eu/nutri/modelos-documento",a.token(),Map.of("tipo","ATESTADO","nome","","texto","Y")),400,"OBRIGATORIO");
		assertThat(requisitar("DELETE","/eu/nutri/modelos-documento/"+id,a.token(),null,Map.of("If-Match","\"2\"")).statusCode()).isEqualTo(204);
		assertThat(json(requisitar("GET","/eu/nutri/modelos-documento",a.token(),null)).path("itens")).hasSize(2);
	}

	@Test void declaracaoAPartirDeConsultaRealizadaFicaImutavelComPdf() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);String url="/pacientes/"+p.fichaId()+"/documentos";
		var criada=consultaCriada(a,p);String consulta=criada.path("id").asString();
		// RN-DOC-02: só consulta REALIZADA
		erro(requisitar("POST",url,a.token(),Map.of("modeloId",DECLARACAO,"consultaId",consulta)),400,"FORMATO_INVALIDO");
		assertThat(requisitar("PATCH","/consultas/"+consulta,a.token(),Map.of("status","REALIZADA"),Map.of("If-Match","\"1\"")).statusCode()).isEqualTo(200);
		var r=requisitar("POST",url,a.token(),Map.of("modeloId",DECLARACAO,"consultaId",consulta));
		assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
		var d=json(r);String id=d.path("id").asString();
		var inicio=Instant.parse(criada.path("inicio").asString()).atZone(ZoneId.of(fuso(a)));
		assertThat(d.path("tipo").asString()).isEqualTo("DECLARACAO_COMPARECIMENTO");assertThat(d.path("titulo").asString()).isEqualTo("Declaração de comparecimento");
		assertThat(d.path("texto").asString()).contains("Paciente fictício",DateTimeFormatter.ofPattern("dd/MM/yyyy").format(inicio),DateTimeFormatter.ofPattern("HH:mm").format(inicio)).doesNotContain("{{");
		String codigo=d.path("codigoConferencia").asString();assertThat(codigo).matches("^[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}$");
		assertThat(d.path("anuladoEm").isNull()||d.path("anuladoEm").isMissingNode()).isTrue();
		// o PDF gravado traz o texto final, o emissor e o código
		var pdf=requisitar("GET","/documentos/"+id+"/pdf",a.token(),null);
		assertThat(pdf.statusCode()).as(pdf.body()).isEqualTo(200);assertThat(pdf.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
		String t=texto(baixar(json(pdf).path("url").asString()).body());
		assertThat(t).contains("DECLARAÇÃO DE COMPARECIMENTO","Paciente fictício","Nutri fictícia","CRN-1 12345",codigo);
		// mudar o modelo ou o nome do paciente depois não muda o documento emitido
		jdbc.update("update ficha_paciente set nome='Outro nome' where id=?::uuid",p.fichaId());
		var depois=json(requisitar("GET",url,a.token(),null)).path("itens");
		assertThat(depois).hasSize(1);assertThat(depois.get(0).path("texto").asString()).isEqualTo(d.path("texto").asString());
		// auditoria da emissão e da leitura
		assertThat(jdbc.queryForObject("select count(*) from log_acesso where usuario_id=? and entidade='documento' and acao='ESCRITA'",Integer.class,a.usuario())).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from log_acesso where usuario_id=? and entidade='documento' and acao='LEITURA'",Integer.class,a.usuario())).isGreaterThanOrEqualTo(2);
	}

	@Test void atestadoSemConsultaUsaDataEPedeOHorarioQuandoOTextoUsa() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);String url="/pacientes/"+p.fichaId()+"/documentos";
		var ok=requisitar("POST",url,a.token(),Map.of("modeloId",ATESTADO,"data","2026-11-10"));
		assertThat(ok.statusCode()).as(ok.body()).isEqualTo(201);assertThat(json(ok).path("texto").asString()).contains("10/11/2026","Paciente fictício");
		// declaração sem consulta e sem horário: o campo {{horario}} fica sem valor
		erro(requisitar("POST",url,a.token(),Map.of("modeloId",DECLARACAO,"data","2026-11-10")),400,"FORMATO_INVALIDO");
		var comHorario=requisitar("POST",url,a.token(),Map.of("modeloId",DECLARACAO,"data","2026-11-10","horario","09:30"));
		assertThat(comHorario.statusCode()).as(comHorario.body()).isEqualTo(201);assertThat(json(comHorario).path("texto").asString()).contains("09:30");
		// texto editado na hora substitui o do modelo; campo desconhecido não passa
		var editado=requisitar("POST",url,a.token(),Map.of("modeloId",ATESTADO,"texto","Atesto que {{paciente}} esteve aqui. {{nutri}}, {{crn}}."));
		assertThat(editado.statusCode()).as(editado.body()).isEqualTo(201);
		assertThat(json(editado).path("texto").asString()).isEqualTo("Atesto que Paciente fictício esteve aqui. Nutri fictícia, CRN-1 12345.");
		erro(requisitar("POST",url,a.token(),Map.of("modeloId",ATESTADO,"texto","CPF {{cpf}}")),400,"FORMATO_INVALIDO");
		erro(requisitar("POST",url,a.token(),Map.of("modeloId",ATESTADO,"horario","25:00")),400,"FORMATO_INVALIDO");
		erro(requisitar("POST",url,a.token(),Map.of()),400,"OBRIGATORIO");
		// mesmo id: não emite duas vezes
		String novo=json(requisitar("POST",url,a.token(),Map.of("modeloId",ATESTADO))).path("id").asString();
		erro(requisitar("POST",url,a.token(),Map.of("id",novo,"modeloId",ATESTADO)),409,"CONFLITO_VERSAO");
		assertThat(json(requisitar("GET",url,a.token(),null)).path("itens")).hasSize(4);
	}

	@Test void anularExigeMotivoEUmaVezSo() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);
		var d=json(requisitar("POST","/pacientes/"+p.fichaId()+"/documentos",a.token(),Map.of("modeloId",ATESTADO)));String id=d.path("id").asString();
		erro(requisitar("POST","/documentos/"+id+"/anular",a.token(),Map.of("motivo"," ")),400,"OBRIGATORIO");
		var ok=requisitar("POST","/documentos/"+id+"/anular",a.token(),Map.of("motivo","Data errada"));
		assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);
		assertThat(json(ok).path("anuladoEm").asString()).isNotBlank();assertThat(json(ok).path("motivoAnulacao").asString()).isEqualTo("Data errada");
		assertThat(json(ok).path("texto").asString()).isEqualTo(d.path("texto").asString());
		erro(requisitar("POST","/documentos/"+id+"/anular",a.token(),Map.of("motivo","De novo")),422,"DOCUMENTO_JA_ANULADO");
		assertThat(jdbc.queryForObject("select count(*) from log_acesso where usuario_id=? and entidade='documento' and acao='ESCRITA'",Integer.class,a.usuario())).isEqualTo(2);
		// o anulado continua na lista, com o motivo
		var lista=json(requisitar("GET","/pacientes/"+p.fichaId()+"/documentos",a.token(),null)).path("itens");
		assertThat(lista).hasSize(1);assertThat(lista.get(0).path("motivoAnulacao").asString()).isEqualTo("Data errada");
	}

	@Test void isolaPorNutriPerfilEVinculo() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();var p=pacienteVinculado(a);var q=pacienteVinculado(b);String url="/pacientes/"+p.fichaId()+"/documentos";
		var copia=json(requisitar("POST","/eu/nutri/modelos-documento",b.token(),Map.of("tipo","ATESTADO","nome","Da B","texto","Atesto {{paciente}}.")));
		var d=json(requisitar("POST",url,a.token(),Map.of("modeloId",ATESTADO)));String id=d.path("id").asString();
		erro(requisitar("GET",url,b.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("POST",url,b.token(),Map.of("modeloId",ATESTADO)),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("POST","/documentos/"+id+"/anular",b.token(),Map.of("motivo","X")),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("GET","/documentos/"+id+"/pdf",b.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		// modelo de outra nutri não serve
		erro(requisitar("POST",url,a.token(),Map.of("modeloId",copia.path("id").asString())),404,"RECURSO_NAO_ENCONTRADO");
		// consulta de outra nutri ou de outro paciente não serve
		String daB=consulta(b,q);requisitar("PATCH","/consultas/"+daB,b.token(),Map.of("status","REALIZADA"),Map.of("If-Match","\"1\""));
		erro(requisitar("POST",url,a.token(),Map.of("modeloId",DECLARACAO,"consultaId",daB)),404,"RECURSO_NAO_ENCONTRADO");
		var outro=pacienteVinculado(a);String deOutro=consulta(a,outro);requisitar("PATCH","/consultas/"+deOutro,a.token(),Map.of("status","REALIZADA"),Map.of("If-Match","\"1\""));
		erro(requisitar("POST",url,a.token(),Map.of("modeloId",DECLARACAO,"consultaId",deOutro)),404,"RECURSO_NAO_ENCONTRADO");
		// paciente não usa as rotas da nutri; sem token, 401
		erro(requisitar("GET",url,p.token(),null),403,"PERFIL_INVALIDO");
		erro(requisitar("GET","/documentos/"+id+"/pdf",p.token(),null),403,"PERFIL_INVALIDO");
		erro(requisitar("GET","/eu/nutri/modelos-documento",p.token(),null),403,"PERFIL_INVALIDO");
		assertThat(requisitar("GET",url,null,null).statusCode()).isEqualTo(401);
		// RN-ASS-05: assinatura inativa deixa a nutri só lendo
		jdbc.update("update assinatura set status='EXPIRADA',fim_periodo_em=now()-interval '30 days' where organizacao_id=?",a.organizacao());
		erro(requisitar("POST",url,a.token(),Map.of("modeloId",ATESTADO)),422,"ASSINATURA_INATIVA");
		erro(requisitar("POST","/documentos/"+id+"/anular",a.token(),Map.of("motivo","X")),422,"ASSINATURA_INATIVA");
		assertThat(requisitar("GET",url,a.token(),null).statusCode()).isEqualTo(200);
		assertThat(requisitar("GET","/documentos/"+id+"/pdf",a.token(),null).statusCode()).isEqualTo(200);
	}

	String consulta(ContaTeste nutri,PacienteTeste p) throws Exception {return consultaCriada(nutri,p).path("id").asString();}
	JsonNode consultaCriada(ContaTeste nutri,PacienteTeste p) throws Exception {
		var m=new HashMap<String,Object>();m.put("pacienteId",p.fichaId());m.put("inicio",Instant.now().plus(1,ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES).toString());m.put("duracaoMin",50);m.put("tipo","RETORNO");
		var r=requisitar("POST","/consultas",nutri.token(),m);assertThat(r.statusCode()).as(r.body()).isEqualTo(201);return json(r);
	}
	String fuso(ContaTeste nutri){return jdbc.queryForObject("select fuso from usuario where id=?",String.class,nutri.usuario());}
	HttpResponse<byte[]> baixar(String url) throws Exception {
		var u=URI.create(url);
		try(var c=HttpClient.newHttpClient()){
			return c.send(HttpRequest.newBuilder(URI.create("http://localhost:"+porta+u.getRawPath()+"?"+u.getRawQuery())).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
		}
	}
	String texto(byte[] pdf) throws Exception {
		try(var doc=org.apache.pdfbox.pdmodel.PDDocument.load(pdf)){return new org.apache.pdfbox.text.PDFTextStripper().getText(doc);}
	}
}
