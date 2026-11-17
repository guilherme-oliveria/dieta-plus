package com.br.dietamais.prescricoes;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.dietas.TesteComPaciente;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** BE-75 (H-05), RN-SUP-01 e 02: suplementos prescritos pela nutri, cópia congelada, isolamento e leitura do paciente. */
class SuplementosApiTest extends TesteComPaciente {
	String hoje(){return LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();}
	Map<String,Object> livre(String nome,String dose){
		var m=new HashMap<String,Object>();m.put("nome",nome);m.put("dose",dose);m.put("horarios",List.of("20:00","08:00"));m.put("inicio",hoje());return m;
	}
	String criarProduto(String token,String nome) throws Exception {
		var n=Map.of("energia_kcal","380","carboidrato_g","8.5","proteina_g","78","lipideos_g","4.2");
		var corpo=new HashMap<String,Object>();corpo.put("nome",nome);corpo.put("medidaCaseira","colher(es) medida(s)");corpo.put("medidaGramas","30");corpo.put("nutrientes",n);
		var r=requisitar("POST","/produtos",token,corpo);assertThat(r.statusCode()).as(r.body()).isEqualTo(201);return json(r).path("id").asString();
	}

	@Test void prescreveComTextoLivreOuProdutoEValida() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);String url="/pacientes/"+p.fichaId()+"/suplementos";
		var ok=requisitar("POST",url,a.token(),livre("Vitamina D","2.000 UI"));assertThat(ok.statusCode()).as(ok.body()).isEqualTo(201);
		assertThat(ok.headers().firstValue("ETag").orElseThrow()).isEqualTo("\"1\"");
		var s=json(ok);assertThat(s.path("nome").asString()).isEqualTo("Vitamina D");assertThat(s.path("horarios").get(0).asString()).isEqualTo("08:00"); // horários em ordem
		// produto da nutri: nome copiado (congelado)
		String produto=criarProduto(a.token(),"Whey de teste");
		var comProduto=new HashMap<String,Object>();comProduto.put("produtoId",produto);comProduto.put("dose","1 colher");comProduto.put("inicio",hoje());
		var criado=requisitar("POST",url,a.token(),comProduto);assertThat(criado.statusCode()).as(criado.body()).isEqualTo(201);
		assertThat(json(criado).path("nome").asString()).isEqualTo("Whey de teste");assertThat(json(criado).path("produtoId").asString()).isEqualTo(produto);
		// produto e nome juntos, nenhum dos dois, produto de outra nutri
		var os=new HashMap<String,Object>(comProduto);os.put("nome","X");erro(requisitar("POST",url,a.token(),os),422,"ITEM_INVALIDO");
		var nenhum=new HashMap<String,Object>();nenhum.put("dose","1");nenhum.put("inicio",hoje());erro(requisitar("POST",url,a.token(),nenhum),422,"ITEM_INVALIDO");
		var outra=cadastrarNutri();var produtoAlheio=criarProduto(outra.token(),"Produto alheio");
		var alheio=new HashMap<String,Object>(comProduto);alheio.put("produtoId",produtoAlheio);erro(requisitar("POST",url,a.token(),alheio),422,"ITEM_INVALIDO");
		var invertido=livre("Ômega 3","1 cápsula");invertido.put("fim",LocalDate.now().minusDays(30).toString());erro(requisitar("POST",url,a.token(),invertido),400,"FORMATO_INVALIDO");
		var semDose=livre("Zinco","");erro(requisitar("POST",url,a.token(),semDose),400,"OBRIGATORIO");
		var horaRuim=livre("Zinco","1");horaRuim.put("horarios",List.of("25:00"));erro(requisitar("POST",url,a.token(),horaRuim),400,"FORMATO_INVALIDO");
		assertThat(json(requisitar("GET",url,a.token(),null)).path("itens")).hasSize(2);
	}

	@Test void ajustaEncerraComVersaoESemMudarONome() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);
		String id=json(requisitar("POST","/pacientes/"+p.fichaId()+"/suplementos",a.token(),livre("Magnésio","300 mg"))).path("id").asString();
		var ok=requisitar("PATCH","/suplementos/"+id,a.token(),Map.of("dose","400 mg"),Map.of("If-Match","\"1\""));
		assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);assertThat(json(ok).path("dose").asString()).isEqualTo("400 mg");assertThat(json(ok).path("versao").asLong()).isEqualTo(2);
		assertThat(json(ok).path("nome").asString()).isEqualTo("Magnésio");
		erro(requisitar("PATCH","/suplementos/"+id,a.token(),Map.of("dose","1 g"),Map.of("If-Match","\"1\"")),409,"CONFLITO_VERSAO");
		erro(requisitar("PATCH","/suplementos/"+id,a.token(),Map.of("dose","1 g")),400,"FORMATO_INVALIDO");
		erro(requisitar("PATCH","/suplementos/"+id,a.token(),Map.of(),Map.of("If-Match","\"2\"")),400,"FORMATO_INVALIDO");
		erro(requisitar("PATCH","/suplementos/"+id,a.token(),Map.of("encerrar",true,"dose","1 g"),Map.of("If-Match","\"2\"")),400,"FORMATO_INVALIDO");
		var fim=requisitar("PATCH","/suplementos/"+id,a.token(),Map.of("encerrar",true),Map.of("If-Match","\"2\""));
		assertThat(fim.statusCode()).as(fim.body()).isEqualTo(200);assertThat(json(fim).path("fim").asString()).isEqualTo(hoje());
	}

	@Test void isolaPorNutriEPacienteEEntregaPeloSyncSemOProduto() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();var p=pacienteVinculado(a);
		String produto=criarProduto(a.token(),"Colágeno de teste");
		var corpo=new HashMap<String,Object>();corpo.put("produtoId",produto);corpo.put("dose","1 sachê");corpo.put("inicio",hoje());
		String id=json(requisitar("POST","/pacientes/"+p.fichaId()+"/suplementos",a.token(),corpo)).path("id").asString();
		erro(requisitar("GET","/pacientes/"+p.fichaId()+"/suplementos",b.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("POST","/pacientes/"+p.fichaId()+"/suplementos",b.token(),livre("X","1")),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("PATCH","/suplementos/"+id,b.token(),Map.of("dose","2"),Map.of("If-Match","\"1\"")),404,"RECURSO_NAO_ENCONTRADO");
		assertThat(requisitar("PATCH","/suplementos/"+id,p.token(),Map.of("dose","2"),Map.of("If-Match","\"1\"")).statusCode()).isEqualTo(403);
		assertThat(requisitar("GET","/pacientes/"+p.fichaId()+"/suplementos",null,null).statusCode()).isEqualTo(401);
		// sync: o paciente recebe sem produtoId; a nutri do vínculo com produtoId; a outra nutri nada
		var paciente=json(requisitar("GET","/sync?limite=1000",p.token(),null)).path("mudancas").path("prescricoesSuplemento");
		assertThat(paciente).hasSize(1);assertThat(paciente.get(0).path("dados").has("produtoId")).isFalse();assertThat(paciente.get(0).path("dados").path("nome").asString()).isEqualTo("Colágeno de teste");
		var nutri=json(requisitar("GET","/sync?limite=1000",a.token(),null)).path("mudancas").path("prescricoesSuplemento");
		assertThat(nutri).hasSize(1);assertThat(nutri.get(0).path("dados").path("produtoId").asString()).isEqualTo(produto);
		assertThat(json(requisitar("GET","/sync?limite=1000",b.token(),null)).path("mudancas").path("prescricoesSuplemento")).isEmpty();
		// RN-ASS-05: com a assinatura da nutri expirada o paciente continua recebendo; a nutri fica só leitura
		jdbc.update("update assinatura set status='EXPIRADA',fim_periodo_em=now()-interval '30 days' where organizacao_id=?",a.organizacao());
		assertThat(json(requisitar("GET","/sync?limite=1000",p.token(),null)).path("mudancas").path("prescricoesSuplemento")).hasSize(1);
		erro(requisitar("POST","/pacientes/"+p.fichaId()+"/suplementos",a.token(),livre("Novo","1")),422,"ASSINATURA_INATIVA");
	}
}
