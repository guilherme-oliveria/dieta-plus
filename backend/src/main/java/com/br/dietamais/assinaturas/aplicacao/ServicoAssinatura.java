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

	/**
	 * RN-ASS-06 e 09: devolve o link do checkout hospedado, mensal ou anual. Contratar não depende da assinatura estar ativa; só quem já paga não
	 * contrata de novo. Plano e periodicidade ficam pendentes até o pagamento ser confirmado pelo webhook (RN-ASS-07).
	 */
	@Transactional
	public CheckoutResposta checkout(String codigoPlano,Periodicidade periodicidade) {
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();
		if(codigoPlano==null||codigoPlano.isBlank())
			throw new ExcecaoNegocio(CodigoErro.OBRIGATORIO,null,"Informe o plano.",List.of(new ErroCampoResposta("planoCodigo","OBRIGATORIO","Informe este campo.")),null);
		var plano=jdbc.query("select id,codigo,nome,preco_mensal,preco_anual,limite_pacientes,a_venda from plano where codigo=? and ativo",
				(rs,n)->new Object[]{rs.getObject("id",UUID.class),plano(rs),rs.getBigDecimal("preco_anual"),rs.getBoolean("a_venda")},codigoPlano)
			.stream().findFirst().orElseThrow(()->new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null));
		var dadosPlano=(PlanoResposta)plano[1];var precoAnual=(BigDecimal)plano[2];
		if(dadosPlano.precoMensal().signum()<=0)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,null,"Este plano não pode ser contratado.",java.util.List.of(),null);
		boolean anual=periodicidade==Periodicidade.ANUAL;
		if(!(Boolean)plano[3]||anual&&(precoAnual==null||precoAnual.signum()<=0))throw new ExcecaoNegocio(CodigoErro.PLANO_INDISPONIVEL,"RN-ASS-09");
		var linhas=jdbc.query("select id,status,gateway_assinatura_id,gateway_cliente_id,plano_pendente_id,periodicidade_pendente from assinatura where organizacao_id=? order by inicio_em desc,id desc limit 1 for update",
			(rs,n)->new Object[]{rs.getObject("id",UUID.class),rs.getString("status"),rs.getString("gateway_assinatura_id"),rs.getString("gateway_cliente_id"),rs.getObject("plano_pendente_id",UUID.class),rs.getString("periodicidade_pendente")},contexto.organizacaoId());
		if(linhas.isEmpty())throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		var linha=linhas.getFirst();var assinaturaId=(UUID)linha[0];var gwId=(String)linha[2];var gwCliente=(String)linha[3];var pendente=(UUID)linha[4];
		var periodicidadePendente=linha[5]==null?Periodicidade.MENSAL:Periodicidade.valueOf((String)linha[5]);
		var resumo=assinaturas.atual();
		if(resumo!=null&&resumo.status()==StatusAssinatura.ATIVA)throw new ExcecaoNegocio(CodigoErro.ASSINATURA_JA_ATIVA,"RN-ASS-06");
		if(!gateway.disponivel())throw new ExcecaoNegocio(CodigoErro.PAGAMENTO_INDISPONIVEL,"RN-ASS-06");
		var planoId=(UUID)plano[0];var escolhida=anual?Periodicidade.ANUAL:Periodicidade.MENSAL;
		if(gwId!=null){
			// mesmo plano e mesma periodicidade: reaproveita a cobrança em aberto
			if(planoId.equals(pendente)&&periodicidadePendente==escolhida){
				var existente=gateway.urlCobranca(gwId);
				if(existente.isPresent())return new CheckoutResposta(existente.get());
			}
			gateway.cancelar(gwId);
		}
		var u=contexto.usuario();
		var criado=gateway.criarCheckout(new GatewayPagamento.DadosCheckout(assinaturaId,u.nome(),u.email(),dadosPlano.codigo(),dadosPlano.nome(),
			anual?precoAnual:dadosPlano.precoMensal(),gwCliente,anual));
		jdbc.update("update assinatura set gateway_assinatura_id=?,gateway_cliente_id=?,plano_pendente_id=?,periodicidade_pendente=? where id=?",
			criado.assinaturaGatewayId(),criado.clienteId(),planoId,escolhida.name(),assinaturaId);
		return new CheckoutResposta(criado.url());
	}

	/** Compatível com quem ainda chama só com o código (mensal). */
	public CheckoutResposta checkout(String codigoPlano){return checkout(codigoPlano,Periodicidade.MENSAL);}

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
