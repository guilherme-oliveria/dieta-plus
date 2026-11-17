package com.br.dietamais.prescricoes;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.dietas.TesteComPaciente;
import java.util.*;
import org.junit.jupiter.api.Test;

/** BE-76 (H-05), RN-ORI-01: orientações avulsas, modelos da nutri, Markdown restrito, push genérico, isolamento e sync. */
class OrientacoesApiTest extends TesteComPaciente {
	@Test void enviaSanitizaEEditaEArquivaComVersao() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);String url="/pacientes/"+p.fichaId()+"/orientacoes";
		var ok=requisitar("POST",url,a.token(),Map.of("titulo","Hidratação","texto","**Beba** água <script>alert(1)</script>\n- devagar"));
		assertThat(ok.statusCode()).as(ok.body()).isEqualTo(201);assertThat(ok.headers().firstValue("ETag").orElseThrow()).isEqualTo("\"1\"");
		var o=json(ok);String id=o.path("id").asString();
		assertThat(o.path("texto").asString()).isEqualTo("**Beba** água \n- devagar");assertThat(o.path("texto").asString()).doesNotContain("<");
		assertThat(o.path("arquivadaEm").isNull()||o.path("arquivadaEm").isMissingNode()).isTrue();
		erro(requisitar("POST",url,a.token(),Map.of("titulo","","texto","x")),400,"OBRIGATORIO");
		erro(requisitar("POST",url,a.token(),Map.of("titulo","<b></b>","texto","x")),400,"FORMATO_INVALIDO");
		erro(requisitar("POST",url,a.token(),Map.of("titulo","Só html","texto","<div></div>")),400,"FORMATO_INVALIDO");
		var edita=requisitar("PATCH","/orientacoes/"+id,a.token(),Map.of("titulo","Hidratação 2"),Map.of("If-Match","\"1\""));
		assertThat(edita.statusCode()).as(edita.body()).isEqualTo(200);assertThat(json(edita).path("versao").asLong()).isEqualTo(2);
		erro(requisitar("PATCH","/orientacoes/"+id,a.token(),Map.of("titulo","X"),Map.of("If-Match","\"1\"")),409,"CONFLITO_VERSAO");
		erro(requisitar("PATCH","/orientacoes/"+id,a.token(),Map.of("titulo","X")),400,"FORMATO_INVALIDO");
		erro(requisitar("PATCH","/orientacoes/"+id,a.token(),Map.of(),Map.of("If-Match","\"2\"")),400,"FORMATO_INVALIDO");
		erro(requisitar("PATCH","/orientacoes/"+id,a.token(),Map.of("arquivar",true,"titulo","X"),Map.of("If-Match","\"2\"")),400,"FORMATO_INVALIDO");
		var arq=requisitar("PATCH","/orientacoes/"+id,a.token(),Map.of("arquivar",true),Map.of("If-Match","\"2\""));
		assertThat(arq.statusCode()).as(arq.body()).isEqualTo(200);assertThat(json(arq).path("arquivadaEm").asString()).isNotBlank();
		assertThat(json(requisitar("GET",url,a.token(),null)).path("itens")).hasSize(1); // arquivada continua na lista da nutri
	}

	@Test void avisaOPacienteComTextoGenerico() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);
		var ok=requisitar("POST","/pacientes/"+p.fichaId()+"/orientacoes",a.token(),Map.of("titulo","Resultado de exame sigiloso","texto","Texto clínico"));assertThat(ok.statusCode()).as(ok.body()).isEqualTo(201);
		var avisos=json(requisitar("GET","/eu/avisos",p.token(),null)).path("itens");
		var aviso=new ArrayList<tools.jackson.databind.JsonNode>();avisos.forEach(x->{if(x.path("tipo").asString().equals("NOVA_ORIENTACAO"))aviso.add(x);});
		assertThat(aviso).hasSize(1);assertThat(aviso.get(0).toString()).doesNotContain("sigiloso").doesNotContain("clínico");assertThat(aviso.get(0).path("titulo").asString()).isEqualTo("Nova orientação");
	}

	@Test void modelosDaNutriComVersaoEIsolamento() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();
		var criado=requisitar("POST","/eu/nutri/modelos-orientacao",a.token(),Map.of("titulo","Pós-consulta","texto","Siga a dieta <i>à risca</i>."));
		assertThat(criado.statusCode()).as(criado.body()).isEqualTo(201);assertThat(criado.headers().firstValue("ETag").orElseThrow()).isEqualTo("\"1\"");
		String id=json(criado).path("id").asString();assertThat(json(criado).path("texto").asString()).isEqualTo("Siga a dieta à risca.");
		assertThat(json(requisitar("GET","/eu/nutri/modelos-orientacao",a.token(),null)).path("itens")).hasSize(1);
		assertThat(json(requisitar("GET","/eu/nutri/modelos-orientacao",b.token(),null)).path("itens")).isEmpty();
		erro(requisitar("PUT","/eu/nutri/modelos-orientacao/"+id,b.token(),Map.of("titulo","X","texto","Y"),Map.of("If-Match","\"1\"")),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("DELETE","/eu/nutri/modelos-orientacao/"+id,b.token(),null,Map.of("If-Match","\"1\"")),404,"RECURSO_NAO_ENCONTRADO");
		var ok=requisitar("PUT","/eu/nutri/modelos-orientacao/"+id,a.token(),Map.of("titulo","Pós-consulta 2","texto","Novo texto"),Map.of("If-Match","\"1\""));
		assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);assertThat(json(ok).path("versao").asLong()).isEqualTo(2);
		erro(requisitar("PUT","/eu/nutri/modelos-orientacao/"+id,a.token(),Map.of("titulo","X","texto","Y"),Map.of("If-Match","\"1\"")),409,"CONFLITO_VERSAO");
		assertThat(requisitar("DELETE","/eu/nutri/modelos-orientacao/"+id,a.token(),null,Map.of("If-Match","\"2\"")).statusCode()).isEqualTo(204);
		assertThat(json(requisitar("GET","/eu/nutri/modelos-orientacao",a.token(),null)).path("itens")).isEmpty();
	}

	@Test void isolaPorNutriEPacienteEEntregaPeloSync() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();var p=pacienteVinculado(a);
		String id=json(requisitar("POST","/pacientes/"+p.fichaId()+"/orientacoes",a.token(),Map.of("titulo","T","texto","Texto"))).path("id").asString();
		erro(requisitar("GET","/pacientes/"+p.fichaId()+"/orientacoes",b.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("POST","/pacientes/"+p.fichaId()+"/orientacoes",b.token(),Map.of("titulo","T","texto","X")),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("PATCH","/orientacoes/"+id,b.token(),Map.of("titulo","X"),Map.of("If-Match","\"1\"")),404,"RECURSO_NAO_ENCONTRADO");
		assertThat(requisitar("PATCH","/orientacoes/"+id,p.token(),Map.of("titulo","X"),Map.of("If-Match","\"1\"")).statusCode()).isEqualTo(403);
		assertThat(requisitar("GET","/pacientes/"+p.fichaId()+"/orientacoes",null,null).statusCode()).isEqualTo(401);
		assertThat(json(requisitar("GET","/sync?limite=1000",p.token(),null)).path("mudancas").path("orientacoes")).hasSize(1);
		assertThat(json(requisitar("GET","/sync?limite=1000",a.token(),null)).path("mudancas").path("orientacoes")).hasSize(1);
		assertThat(json(requisitar("GET","/sync?limite=1000",b.token(),null)).path("mudancas").path("orientacoes")).isEmpty();
		// RN-ASS-05: assinatura da nutri expirada, o paciente continua recebendo; a nutri fica só leitura
		jdbc.update("update assinatura set status='EXPIRADA',fim_periodo_em=now()-interval '30 days' where organizacao_id=?",a.organizacao());
		assertThat(json(requisitar("GET","/sync?limite=1000",p.token(),null)).path("mudancas").path("orientacoes")).hasSize(1);
		erro(requisitar("POST","/pacientes/"+p.fichaId()+"/orientacoes",a.token(),Map.of("titulo","T","texto","X")),422,"ASSINATURA_INATIVA");
	}
}
