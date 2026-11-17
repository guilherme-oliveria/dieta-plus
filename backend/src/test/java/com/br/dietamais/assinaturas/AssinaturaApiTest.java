package com.br.dietamais.assinaturas;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.TestcontainersConfiguration;
import com.br.dietamais.comum.TesteIsolamento;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import({TestcontainersConfiguration.class,GatewayTesteConfig.class})
class AssinaturaApiTest extends TesteIsolamento {
	@Autowired GatewayTesteConfig.GatewayFalso gateway;

	@BeforeEach void ligar(){gateway.ligado=true;gateway.criados=0;gateway.cancelados.clear();gateway.ultimo=null;}

	String gatewayId(ContaTeste c){return jdbc.queryForObject("select gateway_assinatura_id from assinatura where organizacao_id=?",String.class,c.organizacao());}
	String status(ContaTeste c){return jdbc.queryForObject("select status from assinatura where organizacao_id=?",String.class,c.organizacao());}

	void webhook(String corpo,String token,int esperado) throws Exception {
		var pedido=HttpRequest.newBuilder(URI.create("http://localhost:"+porta+"/api/v1/webhooks/pagamento")).header("Content-Type","application/json");
		if(token!=null)pedido.header("asaas-access-token",token);
		try(var http=HttpClient.newHttpClient()){
			var r=http.send(pedido.POST(HttpRequest.BodyPublishers.ofString(corpo)).build(),HttpResponse.BodyHandlers.ofString());
			assertThat(r.statusCode()).as(r.body()).isEqualTo(esperado);
		}
	}

	@Test void detalheMostraTesteELimiteEPlanosPagos() throws Exception {
		var a=cadastrarNutri();
		var d=json(requisitar("GET","/assinatura",a.token(),null));
		assertThat(d.path("status").asString()).isEqualTo("TESTE");assertThat(d.path("plano").path("codigo").asString()).isEqualTo("TESTE");
		assertThat(d.path("pacientesOcupados").asInt()).isZero();assertThat(d.path("carenciaAte").isNull()).isTrue();
		assertThat(d.path("pagamentoDisponivel").asBoolean()).isTrue();
		assertThat(d.path("planosDisponiveis")).hasSize(1);assertThat(d.path("planosDisponiveis").get(0).path("codigo").asString()).isEqualTo("BASICO_20");
		assertThat(requisitar("GET","/assinatura",null,null).statusCode()).isEqualTo(401);
	}

	@Test void checkoutCriaLinkReaproveitaEUsaSoPlanoPago() throws Exception {
		var a=cadastrarNutri();
		var r=json(requisitar("POST","/assinatura/checkout",a.token(),Map.of("plano","BASICO_20")));
		assertThat(r.path("url").asString()).startsWith("https://pagamento.invalid/checkout/");
		assertThat(gatewayId(a)).isNotNull();assertThat(gateway.criados).isEqualTo(1);
		// segunda chamada para o mesmo plano reaproveita a cobrança em aberto
		var de_novo=json(requisitar("POST","/assinatura/checkout",a.token(),Map.of("plano","BASICO_20")));
		assertThat(de_novo.path("url").asString()).startsWith("https://pagamento.invalid/cobranca/");assertThat(gateway.criados).isEqualTo(1);
		erro(requisitar("POST","/assinatura/checkout",a.token(),Map.of("plano","TESTE")),400,"FORMATO_INVALIDO");
		erro(requisitar("POST","/assinatura/checkout",a.token(),Map.of("plano","NAO_EXISTE")),404,"RECURSO_NAO_ENCONTRADO");
		erro(requisitar("POST","/assinatura/checkout",a.token(),Map.of()),400,"OBRIGATORIO");
		gateway.ligado=false;
		var b=cadastrarNutri();
		erro(requisitar("POST","/assinatura/checkout",b.token(),Map.of("plano","BASICO_20")),503,"PAGAMENTO_INDISPONIVEL");
		assertThat(requisitar("POST","/assinatura/checkout",null,Map.of("plano","BASICO_20")).statusCode()).isEqualTo(401);
	}

	@Test void webhookValidaTokenEProcessaUmaVezPorEvento() throws Exception {
		var a=cadastrarNutri();
		requisitar("POST","/assinatura/checkout",a.token(),Map.of("plano","BASICO_20"));
		String gw=gatewayId(a);
		webhook("e1|PAGAMENTO_CONFIRMADO|"+gw+"|2026-11-10",null,401);
		webhook("e1|PAGAMENTO_CONFIRMADO|"+gw+"|2026-11-10","errado",401);
		webhook("lixo","segredo-de-teste",400);
		assertThat(status(a)).isEqualTo("TESTE");
		webhook("e1|PAGAMENTO_CONFIRMADO|"+gw+"|2026-11-10","segredo-de-teste",200);
		assertThat(status(a)).isEqualTo("ATIVA");
		var d=json(requisitar("GET","/assinatura",a.token(),null));
		assertThat(d.path("plano").path("codigo").asString()).isEqualTo("BASICO_20");assertThat(d.path("fimPeriodoEm").asString()).startsWith("2026-12-10");
		// repetido: 200 e sem efeito
		jdbc.update("update assinatura set status='TESTE' where organizacao_id=?",a.organizacao());
		webhook("e1|PAGAMENTO_CONFIRMADO|"+gw+"|2026-11-10","segredo-de-teste",200);
		assertThat(status(a)).isEqualTo("TESTE");
		assertThat(jdbc.queryForObject("select count(*) from evento_pagamento where evento_id='e1'",Integer.class)).isEqualTo(1);
		// quem já paga não contrata de novo
		jdbc.update("update assinatura set status='ATIVA' where organizacao_id=?",a.organizacao());
		erro(requisitar("POST","/assinatura/checkout",a.token(),Map.of("plano","BASICO_20")),409,"ASSINATURA_JA_ATIVA");
		// aviso ASSINATURA chegou na central da nutri
		assertThat(json(requisitar("GET","/eu/avisos",a.token(),null)).path("itens").toString()).contains("ASSINATURA");
	}

	@Test void atrasoCarenciaCancelamentoEEventoDesconhecido() throws Exception {
		var a=cadastrarNutri();
		requisitar("POST","/assinatura/checkout",a.token(),Map.of("plano","BASICO_20"));
		String gw=gatewayId(a);
		webhook("p1|PAGAMENTO_CONFIRMADO|"+gw+"|2026-10-01","segredo-de-teste",200);
		webhook("p2|PAGAMENTO_ATRASADO|"+gw+"|2026-11-01","segredo-de-teste",200);
		assertThat(status(a)).isEqualTo("EM_ATRASO");
		var d=json(requisitar("GET","/assinatura",a.token(),null));
		assertThat(d.path("carenciaAte").asString()).startsWith("2026-11-08");
		webhook("p3|PAGAMENTO_CONFIRMADO|"+gw+"|2026-11-01","segredo-de-teste",200);
		assertThat(status(a)).isEqualTo("ATIVA");
		webhook("x1|PAGAMENTO_CONFIRMADO|sub_inexistente|2026-11-01","segredo-de-teste",200);
		assertThat(jdbc.queryForObject("select resultado from evento_pagamento where evento_id='x1'",String.class)).isEqualTo("ASSINATURA_DESCONHECIDA");
		webhook("c1|ASSINATURA_CANCELADA|"+gw+"|","segredo-de-teste",200);
		assertThat(status(a)).isEqualTo("CANCELADA");assertThat(gatewayId(a)).isNull();
		// cancelada = só leitura (RN-ASS-04)
		erro(requisitar("POST","/pacientes",a.token(),Map.of("nome","Fulano")),422,"ASSINATURA_INATIVA");
		// e pode contratar de novo
		assertThat(requisitar("POST","/assinatura/checkout",a.token(),Map.of("plano","BASICO_20")).statusCode()).isEqualTo(200);
	}

	@Test void cancelarNoTesteSoSoltaOGatewayEMantemOPeriodoDeTeste() throws Exception {
		var a=cadastrarNutri();
		requisitar("POST","/assinatura/checkout",a.token(),Map.of("plano","BASICO_20"));
		String gw=gatewayId(a);
		webhook("c2|ASSINATURA_CANCELADA|"+gw+"|","segredo-de-teste",200);
		assertThat(status(a)).isEqualTo("TESTE");assertThat(gatewayId(a)).isNull();
	}

	/** BE-81a (RN-ASS-06, 09 e 10): checkout anual com planoCodigo, renovação de 1 ano no webhook e plano fora de venda. */
	@Test void checkoutAnualRenovaPorUmAnoEPlanoForaDeVendaEhIndisponivel() throws Exception {
		var a=cadastrarNutri();
		var r=requisitar("POST","/assinatura/checkout",a.token(),Map.of("planoCodigo","BASICO_20","periodicidade","ANUAL"));
		assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
		assertThat(gateway.ultimo.anual()).isTrue();assertThat(gateway.ultimo.valor()).isEqualByComparingTo("299.00");assertThat(gateway.ultimo.planoCodigo()).isEqualTo("BASICO_20");
		// mesmo plano e mesma periodicidade: reaproveita a cobrança; trocar para mensal cancela a anual e cria outra
		requisitar("POST","/assinatura/checkout",a.token(),Map.of("planoCodigo","BASICO_20","periodicidade","ANUAL"));
		assertThat(gateway.criados).isEqualTo(1);
		String anual=gatewayId(a);
		requisitar("POST","/assinatura/checkout",a.token(),Map.of("planoCodigo","BASICO_20","periodicidade","MENSAL"));
		assertThat(gateway.criados).isEqualTo(2);assertThat(gateway.cancelados).contains(anual);
		assertThat(gateway.ultimo.anual()).isFalse();assertThat(gateway.ultimo.valor()).isEqualByComparingTo("29.90");
		// volta para o anual e o pagamento confirma: período de 1 ano a partir do vencimento
		requisitar("POST","/assinatura/checkout",a.token(),Map.of("planoCodigo","BASICO_20","periodicidade","ANUAL"));
		String gw=gatewayId(a);
		webhook("an1|PAGAMENTO_CONFIRMADO|"+gw+"|2026-11-10","segredo-de-teste",200);
		var d=json(requisitar("GET","/assinatura",a.token(),null));
		assertThat(d.path("status").asString()).isEqualTo("ATIVA");assertThat(d.path("periodicidade").asString()).isEqualTo("ANUAL");
		assertThat(d.path("fimPeriodoEm").asString()).startsWith("2027-11-10");
		assertThat(jdbc.queryForObject("select periodicidade_pendente from assinatura where organizacao_id=?",String.class,a.organizacao())).isNull();
		// renovação do ano seguinte pelo webhook
		webhook("an2|PAGAMENTO_CONFIRMADO|"+gw+"|2027-11-10","segredo-de-teste",200);
		assertThat(json(requisitar("GET","/assinatura",a.token(),null)).path("fimPeriodoEm").asString()).startsWith("2028-11-10");
		// plano ativo com preço, mas fora de venda, ou sem preço anual: PLANO_INDISPONIVEL
		var b=cadastrarNutri();
		jdbc.update("update plano set ativo=true,preco_mensal=79.90,preco_anual=null,a_venda=false where codigo='CONSULTORIO'");
		try {
			erro(requisitar("POST","/assinatura/checkout",b.token(),Map.of("planoCodigo","CONSULTORIO")),422,"PLANO_INDISPONIVEL");
			jdbc.update("update plano set a_venda=true where codigo='CONSULTORIO'");
			erro(requisitar("POST","/assinatura/checkout",b.token(),Map.of("planoCodigo","CONSULTORIO","periodicidade","ANUAL")),422,"PLANO_INDISPONIVEL");
			assertThat(requisitar("POST","/assinatura/checkout",b.token(),Map.of("planoCodigo","CONSULTORIO")).statusCode()).isEqualTo(200);
			assertThat(gateway.ultimo.valor()).isEqualByComparingTo("79.90");
		} finally {
			jdbc.update("update plano set ativo=false,preco_mensal=0,a_venda=false where codigo='CONSULTORIO'");
		}
		// sem plano em nenhum dos campos
		erro(requisitar("POST","/assinatura/checkout",b.token(),Map.of("periodicidade","ANUAL")),400,"OBRIGATORIO");
		erro(requisitar("POST","/assinatura/checkout",b.token(),Map.of("planoCodigo","BASICO_20","periodicidade","SEMESTRAL")),400,"FORMATO_INVALIDO");
	}
}
