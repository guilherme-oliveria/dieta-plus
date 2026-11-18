package com.br.dietamais.contas.infra;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.comum.api.FabricaProblema;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import tools.jackson.databind.json.JsonMapper;

class FiltroTaxaLoginTest {
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings={"/api/v1/auth/login","/api/v1/auth/esqueci-senha"})
	void limitaAntesDaAutenticacaoSemConfiarEmIpForjadoNoHeader(String caminho) throws Exception {
		var mapper = JsonMapper.builder().build();
		var filtro = new FiltroTaxaLogin(Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC),new FabricaProblema(mapper),10,10);
		var processados = new AtomicInteger();
		for (int i = 0; i < 11; i++) {
			var req = new MockHttpServletRequest("POST",caminho);
			req.setRemoteAddr("192.0.2.1"); req.addHeader("X-Forwarded-For","192.0.2." + (i + 2));
			var resposta = new MockHttpServletResponse();
			filtro.doFilter(req,resposta,(request,response) -> processados.incrementAndGet());
			if (i == 10) {
				assertThat(resposta.getStatus()).isEqualTo(429);
				assertThat(resposta.getHeader("Retry-After")).isEqualTo("60");
				assertThat(mapper.readTree(resposta.getContentAsString()).path("codigo").asString()).isEqualTo("TAXA_EXCEDIDA");
			}
		}
		assertThat(processados.get()).isEqualTo(10);
	}

	/** BE-80: o endpoint público de planos tem o próprio limite por IP (padrão 60 por minuto), sem afetar o login. */
	@Test void limitaOEndpointPublicoDePlanos() throws Exception {
		var mapper = JsonMapper.builder().build();
		var filtro = new FiltroTaxaLogin(Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC),new FabricaProblema(mapper),10,10,3);
		var processados = new AtomicInteger();
		for (int i = 0; i < 4; i++) {
			var req = new MockHttpServletRequest("GET","/api/v1/planos");req.setRemoteAddr("192.0.2.1");
			var resposta = new MockHttpServletResponse();
			filtro.doFilter(req,resposta,(request,response) -> processados.incrementAndGet());
			if (i == 3) assertThat(resposta.getStatus()).isEqualTo(429);
		}
		assertThat(processados.get()).isEqualTo(3);
		var outra = new MockHttpServletRequest("GET","/api/v1/assinatura");outra.setRemoteAddr("192.0.2.1");
		filtro.doFilter(outra,new MockHttpServletResponse(),(request,response) -> processados.incrementAndGet());
		assertThat(processados.get()).isEqualTo(4);
	}

	/** BE-79, RN-DOC-03: a conferência pública de documentos tem o próprio limite por IP (padrão 30 por minuto). */
	@Test void limitaAConferenciaPublicaDeDocumentos() throws Exception {
		var mapper = JsonMapper.builder().build();
		var filtro = new FiltroTaxaLogin(Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC),new FabricaProblema(mapper),10,10,60,2);
		var processados = new AtomicInteger();
		for (int i = 0; i < 3; i++) {
			var req = new MockHttpServletRequest("GET","/api/v1/documentos/conferir/ABCD-EFGH-JKM" + (i + 2));req.setRemoteAddr("192.0.2.1");
			var resposta = new MockHttpServletResponse();
			filtro.doFilter(req,resposta,(request,response) -> processados.incrementAndGet());
			if (i == 2) {
				assertThat(resposta.getStatus()).isEqualTo(429);assertThat(resposta.getHeader("Retry-After")).isNotBlank();
				assertThat(mapper.readTree(resposta.getContentAsString()).path("codigo").asString()).isEqualTo("TAXA_EXCEDIDA");
			}
		}
		assertThat(processados.get()).isEqualTo(2);
		// outro IP e o endpoint de planos seguem livres
		var outroIp = new MockHttpServletRequest("GET","/api/v1/documentos/conferir/ABCD-EFGH-JKMN");outroIp.setRemoteAddr("192.0.2.9");
		filtro.doFilter(outroIp,new MockHttpServletResponse(),(request,response) -> processados.incrementAndGet());
		var planos = new MockHttpServletRequest("GET","/api/v1/planos");planos.setRemoteAddr("192.0.2.1");
		filtro.doFilter(planos,new MockHttpServletResponse(),(request,response) -> processados.incrementAndGet());
		assertThat(processados.get()).isEqualTo(4);
	}
}
