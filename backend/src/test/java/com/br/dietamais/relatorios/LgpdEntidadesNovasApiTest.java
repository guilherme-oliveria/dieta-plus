package com.br.dietamais.relatorios;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.dietas.TesteComPaciente;
import com.br.dietamais.relatorios.aplicacao.ProcessadorExportacao;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * BE-84: a exportação (RN-LGPD-02) leva as entidades da 1.4.0 (metas e registros, suplementos, orientações, fora do plano, documentos em PDF)
 * só do titular; a leitura das coleções novas do sync pela nutri vai para o `log_acesso`.
 */
@TestPropertySource(properties={"dieta.exportacao.intervalo=PT24H","dieta.exportacao.atraso-inicial=PT24H"})
class LgpdEntidadesNovasApiTest extends TesteComPaciente {
	static final ZoneId FUSO=ZoneId.of("America/Sao_Paulo");
	static final String ATESTADO="019a9000-0000-7000-8000-00000000d001";
	@Autowired ProcessadorExportacao processador;

	String novoId(){return jdbc.queryForObject("select uuidv7()",String.class);}
	LocalDate hoje(){return LocalDate.now(FUSO);}

	/** Cria uma entidade de cada tipo novo para o paciente e devolve o id da meta. */
	String povoar(String token,PacienteTeste p,String marca) throws Exception {
		var meta=new HashMap<String,Object>();meta.put("tipo","SIM_NAO");meta.put("alvo",1);meta.put("frequencia","DIARIA");meta.put("nome","Meta "+marca);
		var m=requisitar("POST","/pacientes/"+p.fichaId()+"/metas",token,meta);assertThat(m.statusCode()).as(m.body()).isEqualTo(201);
		var sup=new HashMap<String,Object>();sup.put("nome","Suplemento "+marca);sup.put("dose","1 cápsula");sup.put("horarios",List.of("08:00"));sup.put("inicio",hoje().toString());
		assertThat(requisitar("POST","/pacientes/"+p.fichaId()+"/suplementos",token,sup).statusCode()).isEqualTo(201);
		assertThat(requisitar("POST","/pacientes/"+p.fichaId()+"/orientacoes",token,Map.of("titulo","Orientação "+marca,"texto","Texto")).statusCode()).isEqualTo(201);
		var d=requisitar("POST","/pacientes/"+p.fichaId()+"/documentos",token,Map.of("modeloId",ATESTADO));assertThat(d.statusCode()).as(d.body()).isEqualTo(201);
		var fora=new HashMap<String,Object>();fora.put("id",novoId());fora.put("data",hoje().toString());fora.put("texto","Fora "+marca);fora.put("registradoEm",Instant.now().toString());
		assertThat(requisitar("POST","/eu/registros-fora-do-plano",p.token(),fora).statusCode()).isEqualTo(201);
		return json(m).path("id").asString();
	}

	Map<String,byte[]> baixar(String url) throws Exception {
		var u=URI.create(url);
		var destino="http://localhost:"+porta+u.getRawPath()+(u.getRawQuery()==null?"":"?"+u.getRawQuery());
		byte[] zip;
		try(var c=HttpClient.newHttpClient()){
			var r=c.send(HttpRequest.newBuilder(URI.create(destino)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertThat(r.statusCode()).isEqualTo(200);zip=r.body();
		}
		var entradas=new LinkedHashMap<String,byte[]>();
		try(var z=new ZipInputStream(new ByteArrayInputStream(zip))){for(var e=z.getNextEntry();e!=null;e=z.getNextEntry())entradas.put(e.getName(),z.readAllBytes());}
		return entradas;
	}

	@Test void exportacaoLevaAsEntidadesNovasSoDoTitular() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);var outro=pacienteVinculado(a);
		String meta=povoar(a.token(),p,"do titular");povoar(a.token(),outro,"do outro");
		var registro=new HashMap<String,Object>();registro.put("id",novoId());registro.put("metaId",meta);registro.put("data",hoje().toString());registro.put("valor",1);
		registro.put("registradoEm",Instant.now().toString());
		assertThat(requisitar("POST","/eu/registros-meta",p.token(),registro).statusCode()).isEqualTo(201);

		assertThat(requisitar("POST","/eu/exportacao",p.token(),null).statusCode()).isEqualTo(202);
		processador.processar();
		var pronta=json(requisitar("GET","/eu/exportacao",p.token(),null));assertThat(pronta.path("status").asString()).isEqualTo("PRONTA");
		var arquivos=baixar(pronta.path("url").asString());
		var dados=new String(arquivos.get("dados.json"),StandardCharsets.UTF_8);
		assertThat(dados).contains("Meta do titular","Suplemento do titular","Orientação do titular","Fora do titular",meta)
			.doesNotContain("do outro");
		var pdfs=arquivos.keySet().stream().filter(n->n.startsWith("documentos/atestado-")&&n.endsWith(".pdf")).toList();
		assertThat(pdfs).hasSize(1);
		assertThat(new String(arquivos.get(pdfs.getFirst()),0,5,StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		assertThat(new String(arquivos.get("LEIAME.txt"),StandardCharsets.UTF_8)).contains("documentos/");
	}

	@Test void leituraDasColecoesNovasPelaNutriVaiParaOLogDeAcesso() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);
		povoar(a.token(),p,"auditada");
		jdbc.update("delete from log_acesso where usuario_id=?",a.usuario());
		var sync=requisitar("GET","/sync?limite=1000",a.token(),null);assertThat(sync.statusCode()).as(sync.body()).isEqualTo(200);
		for(var entidade:List.of("meta_habito","prescricao_suplemento","orientacao","documento"))
			assertThat(jdbc.queryForObject("select count(*) from log_acesso where usuario_id=? and entidade=? and acao='LEITURA'",Integer.class,a.usuario(),entidade))
				.as(entidade).isGreaterThanOrEqualTo(1);
		// o paciente lendo o próprio sync não gera registro de nutri
		jdbc.update("delete from log_acesso");
		assertThat(requisitar("GET","/sync?limite=1000",p.token(),null).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select count(*) from log_acesso where entidade in ('meta_habito','prescricao_suplemento','orientacao','documento')",Integer.class)).isZero();
	}
}
