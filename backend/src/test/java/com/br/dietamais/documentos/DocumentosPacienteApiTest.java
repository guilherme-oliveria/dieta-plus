package com.br.dietamais.documentos;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.dietas.TesteComPaciente;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** BE-79 (H-06), RN-DOC-02 e 03: o paciente lista e baixa o que recebeu, o sync leva só metadados e a conferência pública não expõe o paciente. */
class DocumentosPacienteApiTest extends TesteComPaciente {
	static final String ATESTADO=DocumentosApiTest.ATESTADO;

	@Test void pacienteListaEBaixaOQueRecebeuInclusiveAnuladoEComVinculoEncerrado() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);
		var d=json(requisitar("POST","/pacientes/"+p.fichaId()+"/documentos",a.token(),Map.of("modeloId",ATESTADO)));String id=d.path("id").asString();
		var r=requisitar("GET","/eu/documentos",p.token(),null);
		assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(r.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
		var itens=json(r).path("itens");assertThat(itens).hasSize(1);
		var item=itens.get(0);
		assertThat(item.path("id").asString()).isEqualTo(id);assertThat(item.path("tipo").asString()).isEqualTo("ATESTADO");assertThat(item.path("titulo").asString()).isEqualTo("Atestado");
		assertThat(item.path("anulado").asBoolean()).isFalse();
		// só metadados: nem texto, nem código, nem motivo
		assertThat(item.has("texto")).isFalse();assertThat(item.has("codigoConferencia")).isFalse();assertThat(item.has("motivoAnulacao")).isFalse();
		var pdf=requisitar("GET","/eu/documentos/"+id+"/pdf",p.token(),null);
		assertThat(pdf.statusCode()).as(pdf.body()).isEqualTo(200);assertThat(pdf.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
		var arquivo=baixar(json(pdf).path("url").asString());
		assertThat(arquivo.statusCode()).isEqualTo(200);assertThat(new String(arquivo.body(),0,5)).isEqualTo("%PDF-");
		assertThat(arquivo.headers().firstValue("Content-Disposition").orElse("")).contains("atestado-");
		assertThat(jdbc.queryForObject("select count(*) from log_acesso where usuario_id=?::uuid and entidade='documento' and acao='LEITURA'",Integer.class,p.usuarioId())).isEqualTo(1);
		// anulado continua na lista do paciente, marcado; o PDF ainda baixa
		requisitar("POST","/documentos/"+id+"/anular",a.token(),Map.of("motivo","Data errada"));
		var depois=json(requisitar("GET","/eu/documentos",p.token(),null)).path("itens");
		assertThat(depois.get(0).path("anulado").asBoolean()).isTrue();assertThat(depois.get(0).has("motivoAnulacao")).isFalse();
		assertThat(requisitar("GET","/eu/documentos/"+id+"/pdf",p.token(),null).statusCode()).isEqualTo(200);
		// vínculo encerrado: o paciente continua lendo o que recebeu
		jdbc.update("update vinculo set status='ENCERRADO' where id=?::uuid",p.vinculoId());
		assertThat(json(requisitar("GET","/eu/documentos",p.token(),null)).path("itens")).hasSize(1);
		assertThat(requisitar("GET","/eu/documentos/"+id+"/pdf",p.token(),null).statusCode()).isEqualTo(200);
	}

	@Test void isolaPorPacienteEPerfil() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();var p=pacienteVinculado(a);var outro=pacienteVinculado(a);var deB=pacienteVinculado(b);
		String id=json(requisitar("POST","/pacientes/"+p.fichaId()+"/documentos",a.token(),Map.of("modeloId",ATESTADO))).path("id").asString();
		assertThat(json(requisitar("GET","/eu/documentos",outro.token(),null)).path("itens")).isEmpty();
		assertThat(json(requisitar("GET","/eu/documentos",deB.token(),null)).path("itens")).isEmpty();
		erro(requisitar("GET","/eu/documentos/"+id+"/pdf",outro.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("GET","/eu/documentos/"+id+"/pdf",deB.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("GET","/eu/documentos/"+UUID.randomUUID()+"/pdf",p.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		// a nutri usa as rotas dela, não as do paciente
		erro(requisitar("GET","/eu/documentos",a.token(),null),403,"PERFIL_INVALIDO");
		erro(requisitar("GET","/eu/documentos/"+id+"/pdf",a.token(),null),403,"PERFIL_INVALIDO");
		assertThat(requisitar("GET","/eu/documentos",null,null).statusCode()).isEqualTo(401);
	}

	@Test void syncLevaSoMetadadosParaNutriEPaciente() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();var p=pacienteVinculado(a);var outro=pacienteVinculado(a);
		String id=json(requisitar("POST","/pacientes/"+p.fichaId()+"/documentos",a.token(),Map.of("modeloId",ATESTADO))).path("id").asString();
		for(var token:List.of(p.token(),a.token())){
			var docs=json(requisitar("GET","/sync?limite=1000",token,null)).path("mudancas").path("documentos");
			assertThat(docs).hasSize(1);
			var dados=docs.get(0).path("dados");
			assertThat(docs.get(0).path("id").asString()).isEqualTo(id);assertThat(dados.path("tipo").asString()).isEqualTo("ATESTADO");
			assertThat(dados.has("texto")).isFalse();assertThat(dados.has("codigoConferencia")).isFalse();
		}
		assertThat(json(requisitar("GET","/sync?limite=1000",b.token(),null)).path("mudancas").path("documentos")).isEmpty();
		assertThat(json(requisitar("GET","/sync?limite=1000",outro.token(),null)).path("mudancas").path("documentos")).isEmpty();
		// anular gera nova mudança, com anulado=true
		requisitar("POST","/documentos/"+id+"/anular",a.token(),Map.of("motivo","Erro"));
		var docs=json(requisitar("GET","/sync?limite=1000",p.token(),null)).path("mudancas").path("documentos");
		assertThat(docs).hasSize(1);assertThat(docs.get(0).path("dados").path("anulado").asBoolean()).isTrue();
	}

	@Test void conferenciaPublicaMostraSituacaoSemDadoDoPacienteERegistraTentativas() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);
		var d=json(requisitar("POST","/pacientes/"+p.fichaId()+"/documentos",a.token(),Map.of("modeloId",ATESTADO)));
		String id=d.path("id").asString();String codigo=d.path("codigoConferencia").asString();
		// sem login, aceita minúsculas e sem hífen
		var r=requisitar("GET","/documentos/conferir/"+codigo.replace("-","").toLowerCase(Locale.ROOT),null,null);
		assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(r.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
		var c=json(r);
		assertThat(c.path("tipo").asString()).isEqualTo("ATESTADO");assertThat(c.path("emissor").asString()).isEqualTo("Nutri fictícia");
		assertThat(c.path("crn").asString()).isEqualTo("CRN-1 12345");assertThat(c.path("situacao").asString()).isEqualTo("VALIDO");
		assertThat(c.path("emitidoEm").asString()).isNotBlank();
		// RN-DOC-03: nada do paciente, nem o texto
		assertThat(r.body()).doesNotContain("Paciente","fictício",p.pacienteId(),p.fichaId(),id);
		assertThat(c.has("texto")).isFalse();
		requisitar("POST","/documentos/"+id+"/anular",a.token(),Map.of("motivo","Data errada"));
		var anulado=requisitar("GET","/documentos/conferir/"+codigo,null,null);
		assertThat(json(anulado).path("situacao").asString()).isEqualTo("ANULADO");assertThat(anulado.body()).doesNotContain("Data errada");
		// inexistente ou fora do formato: 404, sem pista
		erro(requisitar("GET","/documentos/conferir/ZZZZ-ZZZZ-ZZZZ",null,null),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("GET","/documentos/conferir/0000",null,null),404,"RECURSO_NAO_ENCONTRADO");
		// toda tentativa fica registrada, achada ou não
		assertThat(jdbc.queryForObject("select count(*) from conferencia_documento where documento_id=?::uuid and encontrado",Integer.class,id)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from conferencia_documento where documento_id is null and not encontrado",Integer.class)).isGreaterThanOrEqualTo(2);
	}

	HttpResponse<byte[]> baixar(String url) throws Exception {
		var u=URI.create(url);
		try(var c=HttpClient.newHttpClient()){
			return c.send(HttpRequest.newBuilder(URI.create("http://localhost:"+porta+u.getRawPath()+"?"+u.getRawQuery())).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
		}
	}
}
