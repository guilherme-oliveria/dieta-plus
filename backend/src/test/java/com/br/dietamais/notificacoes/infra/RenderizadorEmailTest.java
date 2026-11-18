package com.br.dietamais.notificacoes.infra;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.notificacoes.api.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class RenderizadorEmailTest {
	RenderizadorEmail renderizador(String url) {
		var resolver=new ClassLoaderTemplateResolver();resolver.setPrefix("templates/");resolver.setSuffix(".html");resolver.setCharacterEncoding("UTF-8");
		var engine=new SpringTemplateEngine();engine.setTemplateResolver(resolver);
		return new RenderizadorEmail(engine,url,"https://app.example.invalid/redefinir-senha");
	}
	@Test void templatesTemLinkConfiguradoEPrazosSemDadosClinicos() {
		var renderizador=renderizador("dietamais://confirmar-email");
		var confirmar=renderizador.renderizar(new EmailTransacional(TipoEmail.CONFIRMAR_EMAIL,"ficticio@example.invalid","TOKEN_SEM_VALIDADE",Instant.parse("2026-10-26T12:00:00Z")));
		assertThat(confirmar.html()).contains("dietamais://confirmar-email?token=TOKEN_SEM_VALIDADE","24 horas").doesNotContain("th:href=");
		var redefinir=renderizador.renderizar(new EmailTransacional(TipoEmail.REDEFINIR_SENHA,"ficticio@example.invalid","TOKEN_SEM_VALIDADE",Instant.parse("2026-10-25T13:00:00Z")));
		assertThat(redefinir.html()).contains("https://app.example.invalid/redefinir-senha?token=TOKEN_SEM_VALIDADE","uma hora");
		assertThat(redefinir.toString()).doesNotContain("ficticio@example.invalid","TOKEN_SEM_VALIDADE");
	}
	@Test void linksNaoAceitamScriptOuHostDerivadoDaRequisicao() {
		assertThatThrownBy(()->renderizador("javascript:SEM_VALIDADE")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(()->renderizador("https://app.example.invalid?token=SEM_VALIDADE")).isInstanceOf(IllegalStateException.class);
	}
	@Test void conviteTemCodigoEPrazoSemIdentidadeOuDadosClinicosNoTexto() {
		var convite=renderizador("dietamais://confirmar-email").renderizar(new EmailTransacional(TipoEmail.CONVITE,"ficticio@example.invalid","AB2CD3EF",Instant.parse("2026-11-02T12:00:00Z")));
		assertThat(convite.html()).contains("dietamais://convites/AB2CD3EF","2026-11-02T12:00:00Z").doesNotContain("ficticio@example.invalid","redefinir-senha");
		assertThat(convite.assunto()).contains("convite");
	}
	@Test void emailDoFimDoTesteSoTemOPrazo() {
		var r=renderizador("dietamais://confirmar-email");
		var sete=r.renderizar(new EmailTransacional(TipoEmail.FIM_TESTE,"ficticio@example.invalid","7",Instant.parse("2026-10-26T12:00:00Z")));
		assertThat(sete.assunto()).contains("teste");assertThat(sete.html()).contains("7 dias").doesNotContain("th:text");
		assertThat(r.renderizar(new EmailTransacional(TipoEmail.FIM_TESTE,"ficticio@example.invalid","1",Instant.parse("2026-10-26T12:00:00Z"))).html()).contains("1 dia").doesNotContain("1 dias");
	}
	@Test void confirmacaoDeConsultaTemDataHoraELinkSoNaOnline() {
		var r=renderizador("dietamais://confirmar-email");var inicio=Instant.parse("2026-12-01T17:00:00Z");
		var online=r.renderizar(new EmailTransacional(TipoEmail.CONSULTA_AGENDADA,"ficticio@example.invalid",null,inicio,inicio,"https://meet.example.invalid/sala-1"));
		assertThat(online.assunto()).contains("consulta");
		assertThat(online.html()).contains("01/12/2026","14:00","https://meet.example.invalid/sala-1").doesNotContain("th:text","ficticio@example.invalid");
		var presencial=r.renderizar(new EmailTransacional(TipoEmail.CONSULTA_AGENDADA,"ficticio@example.invalid",null,inicio,inicio,null));
		assertThat(presencial.html()).contains("01/12/2026","14:00").doesNotContain("online","href");
	}
}
