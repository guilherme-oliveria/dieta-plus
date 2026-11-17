package com.br.dietamais.assinaturas.aplicacao;

import com.br.dietamais.assinaturas.api.*;
import com.br.dietamais.comum.api.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoAssinatura {
	private final JdbcTemplate jdbc;private final UsuarioAtual usuario;private final ConsultarAssinatura assinaturas;
	private final GatewayPagamento gateway;private final Clock relogio;private final Duration carencia;private final List<String> plataformasSemPreco;
	public ServicoAssinatura(JdbcTemplate jdbc,UsuarioAtual usuario,ConsultarAssinatura assinaturas,GatewayPagamento gateway,Clock relogio,
			@Value("${dieta.assinatura.carencia:P7D}") Duration carencia,
			@Value("${dieta.assinatura.plataformas-sem-preco:}") List<String> plataformasSemPreco) {
		this.plataformasSemPreco=plataformasSemPreco.stream().map(p->p.strip().toUpperCase()).filter(p->!p.isEmpty()).toList();
		this.jdbc=jdbc;this.usuario=usuario;this.assinaturas=assinaturas;this.gateway=gateway;this.relogio=relogio;this.carencia=carencia;
	}

	@Transactional(readOnly=true)
	public AssinaturaDetalheResposta detalhe(String plataforma) {
		var resumo=assinaturas.atual();
		if(resumo==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		var plano=jdbc.queryForObject("select codigo,nome,preco_mensal,limite_pacientes from plano where codigo=?",(rs,n)->plano(rs),resumo.plano());
		var periodicidade=Periodicidade.valueOf(jdbc.queryForObject("select periodicidade from assinatura where organizacao_id=? order by inicio_em desc,id desc limit 1",String.class,resumo.organizacaoId()));
		Integer diasTeste=null;
		if(resumo.status()==StatusAssinatura.TESTE&&resumo.fimPeriodoEm()!=null)diasTeste=(int)Math.max(0,(Duration.between(relogio.instant(),resumo.fimPeriodoEm()).toSeconds()+86399)/86400);
		boolean exibe=plataforma==null||!plataformasSemPreco.contains(plataforma.strip().toUpperCase());
		var carenciaAte=resumo.status()==StatusAssinatura.EM_ATRASO&&resumo.fimPeriodoEm()!=null?resumo.fimPeriodoEm().plus(carencia):null;
		return new AssinaturaDetalheResposta(resumo.organizacaoId(),plano,resumo.status(),resumo.pacientesOcupados(),resumo.fimPeriodoEm(),carenciaAte,
			gateway.disponivel(),planosPagos(),periodicidade,diasTeste,exibe);
	}

	/** RN-ASS-06: devolve o link do checkout hospedado. Contratar não depende da assinatura estar ativa; só quem já paga não contrata de novo. */
	@Transactional
	public CheckoutResposta checkout(String codigoPlano) {
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();
		var plano=jdbc.query("select id,codigo,nome,preco_mensal,limite_pacientes from plano where codigo=? and ativo",(rs,n)->new Object[]{rs.getObject("id",UUID.class),plano(rs)},codigoPlano)
			.stream().findFirst().orElseThrow(()->new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null));
		var dadosPlano=(PlanoResposta)plano[1];
		if(dadosPlano.precoMensal().signum()<=0)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,null,"Este plano não pode ser contratado.",java.util.List.of(),null);
		var linhas=jdbc.query("select id,status,gateway_assinatura_id,gateway_cliente_id,plano_pendente_id from assinatura where organizacao_id=? order by inicio_em desc,id desc limit 1 for update",
			(rs,n)->new Object[]{rs.getObject("id",UUID.class),rs.getString("status"),rs.getString("gateway_assinatura_id"),rs.getString("gateway_cliente_id"),rs.getObject("plano_pendente_id",UUID.class)},contexto.organizacaoId());
		if(linhas.isEmpty())throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		var linha=linhas.getFirst();var assinaturaId=(UUID)linha[0];var gwId=(String)linha[2];var gwCliente=(String)linha[3];var pendente=(UUID)linha[4];
		var resumo=assinaturas.atual();
		if(resumo!=null&&resumo.status()==StatusAssinatura.ATIVA)throw new ExcecaoNegocio(CodigoErro.ASSINATURA_JA_ATIVA,"RN-ASS-06");
		if(!gateway.disponivel())throw new ExcecaoNegocio(CodigoErro.PAGAMENTO_INDISPONIVEL,"RN-ASS-06");
		var planoId=(UUID)plano[0];
		if(gwId!=null){
			if(planoId.equals(pendente)){
				var existente=gateway.urlCobranca(gwId);
				if(existente.isPresent())return new CheckoutResposta(existente.get());
			}
			gateway.cancelar(gwId);
		}
		var u=contexto.usuario();
		var criado=gateway.criarCheckout(new GatewayPagamento.DadosCheckout(assinaturaId,u.nome(),u.email(),dadosPlano.codigo(),dadosPlano.nome(),dadosPlano.precoMensal(),gwCliente));
		jdbc.update("update assinatura set gateway_assinatura_id=?,gateway_cliente_id=?,plano_pendente_id=? where id=?",criado.assinaturaGatewayId(),criado.clienteId(),planoId,assinaturaId);
		return new CheckoutResposta(criado.url());
	}

	/** Planos à venda (RN-ASS-09), os mesmos de `GET /api/v1/planos`. */
	@Transactional(readOnly=true)
	public List<PlanoPublicoResposta> planosPagos() {
		return jdbc.query("select codigo,nome,preco_mensal,preco_anual,limite_pacientes from plano where ativo and a_venda and preco_mensal>0 order by preco_mensal,codigo",
			(rs,n)->new PlanoPublicoResposta(rs.getString("codigo"),rs.getString("nome"),rs.getBigDecimal("preco_mensal"),rs.getBigDecimal("preco_anual"),rs.getObject("limite_pacientes",Integer.class)));
	}
	private static PlanoResposta plano(java.sql.ResultSet rs) throws java.sql.SQLException {
		BigDecimal preco=rs.getBigDecimal("preco_mensal");
		return new PlanoResposta(rs.getString("codigo"),rs.getString("nome"),preco,rs.getObject("limite_pacientes",Integer.class));
	}
}
