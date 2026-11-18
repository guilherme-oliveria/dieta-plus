package com.br.dietamais.contas.infra;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.contas.dominio.LimiteTaxaLogin;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+20)
public class FiltroTaxaLogin extends OncePerRequestFilter {
	private final LimiteTaxaLogin limite;
	private final LimiteTaxaLogin recuperacao;
	private final LimiteTaxaLogin planos;
	private final LimiteTaxaLogin conferencia;
	private static final String CONFERIR="/api/v1/documentos/conferir/";
	private final Clock relogio;
	private final FabricaProblema problemas;
	public FiltroTaxaLogin(Clock relogio,FabricaProblema problemas,int quantidade,int limiteRecuperacao) {
		this(relogio,problemas,quantidade,limiteRecuperacao,60,30);
	}
	public FiltroTaxaLogin(Clock relogio,FabricaProblema problemas,int quantidade,int limiteRecuperacao,int limitePlanos) {
		this(relogio,problemas,quantidade,limiteRecuperacao,limitePlanos,30);
	}
	@org.springframework.beans.factory.annotation.Autowired
	public FiltroTaxaLogin(Clock relogio,FabricaProblema problemas,@Value("${dieta.auth.login-limite-ip:10}") int quantidade,
			@Value("${dieta.auth.recuperacao-limite-ip:10}") int limiteRecuperacao,@Value("${dieta.planos.limite-ip:60}") int limitePlanos,
			@Value("${dieta.documentos.conferir-limite-ip:30}") int limiteConferencia) {
		this.relogio=relogio;this.problemas=problemas;limite=new LimiteTaxaLogin(quantidade,10000);
		recuperacao=new LimiteTaxaLogin(limiteRecuperacao,10000);planos=new LimiteTaxaLogin(limitePlanos,10000);
		conferencia=new LimiteTaxaLogin(limiteConferencia,10000);
	}
	@Override protected boolean shouldNotFilter(HttpServletRequest req) {
		if("GET".equals(req.getMethod())&&"/api/v1/planos".equals(req.getRequestURI()))return false; // BE-80: endpoint público de planos
		if("GET".equals(req.getMethod())&&req.getRequestURI().startsWith(CONFERIR))return false; // BE-79, RN-DOC-03: conferência pública de documentos
		return !"POST".equals(req.getMethod())||!java.util.Set.of("/api/v1/auth/login","/api/v1/auth/esqueci-senha").contains(req.getRequestURI());
	}
	@Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain cadeia) throws IOException,ServletException {
		var limitador=req.getRequestURI().startsWith(CONFERIR)?conferencia:req.getRequestURI().equals("/api/v1/planos")?planos:req.getRequestURI().equals("/api/v1/auth/login")?limite:recuperacao;
		long espera=limitador.espera(req.getRemoteAddr(),relogio.instant());
		if(espera>0) {
			res.setHeader("Retry-After",Long.toString(espera));res.setHeader("Cache-Control","no-store");
			problemas.escrever(res,CodigoErro.TAXA_EXCEDIDA,"Aguarde antes de tentar novamente.");return;
		}
		cadeia.doFilter(req,res);
	}
}
