package com.br.dietamais.pacientes.api;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.dietas.TesteComPaciente;
import java.util.*;
import org.junit.jupiter.api.Test;

/** BE-68: itens de saúde na ficha (RN-SAU-01 e 02): escrita, preservação, validação, sync da nutri e isolamento. */
class RestricoesSaudeApiTest extends TesteComPaciente {
	static Map<String,Object> item(String tipo,String nome,Object... resto){
		var m=new LinkedHashMap<String,Object>();m.put("tipo",tipo);m.put("nome",nome);
		for(int i=0;i<resto.length;i+=2)m.put((String)resto[i],resto[i+1]);return m;
	}
	Map<String,String> ifMatch(long v){return Map.of("If-Match","\""+v+"\"");}

	@Test void criaLeEditaEPreservaOsItensQuandoOClienteNaoOsEnvia() throws Exception {
		var a=cadastrarNutri();
		var corpo=new HashMap<String,Object>(Map.of("nome","Paciente fictício"));
		corpo.put("restricoesSaude",List.of(item("ALERGIA","Amendoim","gravidade","GRAVE","marcadores",List.of("AMENDOIM")),item("MEDICAMENTO","Metformina")));
		var criada=requisitar("POST","/pacientes",a.token(),corpo);assertThat(criada.statusCode()).as(criada.body()).isEqualTo(201);
		var p=json(criada);String id=p.path("id").asString();var itens=p.path("restricoesSaude");
		assertThat(itens).hasSize(2);assertThat(itens.get(0).path("id").asString()).isNotBlank();assertThat(itens.get(0).path("marcadores").get(0).asString()).isEqualTo("AMENDOIM");
		assertThat(itens.get(1).has("marcadores")).isFalse();
		assertThat(json(requisitar("GET","/pacientes/"+id,a.token(),null)).path("restricoesSaude")).hasSize(2);
		// cliente antigo edita sem o campo: os itens ficam
		var semCampo=requisitar("PATCH","/pacientes/"+id,a.token(),Map.of("nome","Nome novo"),ifMatch(0));assertThat(semCampo.statusCode()).as(semCampo.body()).isEqualTo(200);
		assertThat(json(semCampo).path("restricoesSaude")).hasSize(2);
		// com o campo substitui a lista inteira
		var novo=new HashMap<String,Object>(Map.of("nome","Nome novo"));novo.put("restricoesSaude",List.of(item("INTOLERANCIA","Lactose","marcadores",List.of("CONTEM_LACTOSE"))));
		var trocado=requisitar("PATCH","/pacientes/"+id,a.token(),novo,ifMatch(1));assertThat(trocado.statusCode()).as(trocado.body()).isEqualTo(200);
		assertThat(json(trocado).path("restricoesSaude")).hasSize(1);assertThat(json(trocado).path("restricoesSaude").get(0).path("nome").asString()).isEqualTo("Lactose");
		// lista vazia limpa e some da resposta
		var limpo=new HashMap<String,Object>(Map.of("nome","Nome novo"));limpo.put("restricoesSaude",List.of());
		var apagado=requisitar("PATCH","/pacientes/"+id,a.token(),limpo,ifMatch(2));assertThat(apagado.statusCode()).as(apagado.body()).isEqualTo(200);
		assertThat(json(apagado).has("restricoesSaude")).isFalse();
	}

	@Test void aceitaIdDoAppEValidaMarcadorTipoEDuplicidade() throws Exception {
		var a=cadastrarNutri();String v7="019b0000-0000-7000-8000-000000000001";
		var ok=new HashMap<String,Object>(Map.of("nome","P"));ok.put("restricoesSaude",List.of(item("ALERGIA","Ovo","id",v7,"marcadores",List.of("OVO"))));
		var criada=requisitar("POST","/pacientes",a.token(),ok);assertThat(criada.statusCode()).as(criada.body()).isEqualTo(201);
		assertThat(json(criada).path("restricoesSaude").get(0).path("id").asString()).isEqualTo(v7);
		// marcador em condição ou medicamento: ITEM_INVALIDO (RN-SAU-02)
		var condicao=new HashMap<String,Object>(Map.of("nome","P"));condicao.put("restricoesSaude",List.of(item("CONDICAO","Diabetes","marcadores",List.of("ACUCAR_ADICIONADO"))));
		erro(requisitar("POST","/pacientes",a.token(),condicao),422,"ITEM_INVALIDO");
		// formato do marcador, id que não é UUIDv7 e id repetido
		var formato=new HashMap<String,Object>(Map.of("nome","P"));formato.put("restricoesSaude",List.of(item("ALERGIA","Ovo","marcadores",List.of("ovo!"))));
		erro(requisitar("POST","/pacientes",a.token(),formato),400,"FORMATO_INVALIDO");
		var v4=new HashMap<String,Object>(Map.of("nome","P"));v4.put("restricoesSaude",List.of(item("ALERGIA","Ovo","id",UUID.randomUUID().toString())));
		erro(requisitar("POST","/pacientes",a.token(),v4),400,"FORMATO_INVALIDO");
		var repetido=new HashMap<String,Object>(Map.of("nome","P"));repetido.put("restricoesSaude",List.of(item("ALERGIA","Ovo","id",v7),item("INTOLERANCIA","Soja","id",v7)));
		erro(requisitar("POST","/pacientes",a.token(),repetido),400,"FORMATO_INVALIDO");
		// nome em branco, nome longo e gravidade desconhecida: validação do corpo
		var vazio=new HashMap<String,Object>(Map.of("nome","P"));vazio.put("restricoesSaude",List.of(item("ALERGIA"," ")));
		erro(requisitar("POST","/pacientes",a.token(),vazio),400,"FORMATO_INVALIDO");
		var grav=new HashMap<String,Object>(Map.of("nome","P"));grav.put("restricoesSaude",List.of(item("ALERGIA","Ovo","gravidade","ENORME")));
		erro(requisitar("POST","/pacientes",a.token(),grav),400,"FORMATO_INVALIDO");
	}

	@Test void vaiNoSyncDaNutriMasNuncaChegaAoPacienteNemAOutraNutri() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();var paciente=pacienteVinculado(a);
		var corpo=new HashMap<String,Object>(Map.of("nome","Paciente fictício"));
		corpo.put("restricoesSaude",List.of(item("ALERGIA","SegredoClinicoAmendoim","gravidade","GRAVE","observacao","ObservacaoReservada","marcadores",List.of("AMENDOIM"))));
		var editada=requisitar("PATCH","/pacientes/"+paciente.fichaId(),a.token(),corpo,ifMatch(jsonVersao(a,paciente.fichaId())));assertThat(editada.statusCode()).as(editada.body()).isEqualTo(200);
		var syncNutri=requisitar("GET","/sync?limite=1000",a.token(),null);assertThat(syncNutri.statusCode()).as(syncNutri.body()).isEqualTo(200);
		assertThat(syncNutri.body()).contains("SegredoClinicoAmendoim");
		var syncPaciente=requisitar("GET","/sync?limite=1000",paciente.token(),null);assertThat(syncPaciente.statusCode()).as(syncPaciente.body()).isEqualTo(200);
		assertThat(syncPaciente.body()).doesNotContain("SegredoClinicoAmendoim").doesNotContain("ObservacaoReservada").doesNotContain("restricoesSaude");
		// o paciente nem enxerga a rota da ficha da nutri
		assertThat(requisitar("GET","/pacientes/"+paciente.fichaId(),paciente.token(),null).statusCode()).isIn(403,404);
		// outra nutri: 404
		erro(requisitar("GET","/pacientes/"+paciente.fichaId(),b.token(),null),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("PATCH","/pacientes/"+paciente.fichaId(),b.token(),corpo,ifMatch(1)),404,"RECURSO_NAO_ENCONTRADO");
	}
	long jsonVersao(ContaTeste nutri,String ficha) throws Exception {return json(requisitar("GET","/pacientes/"+ficha,nutri.token(),null)).path("versao").asLong();}
}
