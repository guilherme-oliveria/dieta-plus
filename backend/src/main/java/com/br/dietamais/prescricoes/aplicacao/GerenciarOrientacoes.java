package com.br.dietamais.prescricoes.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.pacientes.api.*;
import com.br.dietamais.prescricoes.api.*;
import com.br.dietamais.prescricoes.dominio.MarkdownRestrito;
import com.br.dietamais.prescricoes.infra.OrientacaoStore;
import java.time.Clock;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** RN-ORI-01: a nutri envia, edita e arquiva orientações avulsas (online); o texto é Markdown restrito e o HTML nunca é guardado. */
@Service
public class GerenciarOrientacoes {
	private final OrientacaoStore orientacoes;private final ConsultarFichaParaLista fichas;private final UsuarioAtual usuario;private final GuardaEscritaNutri guarda;
	private final AuditoriaClinica auditoria;private final GeradorIds ids;private final Clock relogio;private final JsonMapper mapper;private final ApplicationEventPublisher eventos;
	public GerenciarOrientacoes(OrientacaoStore orientacoes,ConsultarFichaParaLista fichas,UsuarioAtual usuario,GuardaEscritaNutri guarda,AuditoriaClinica auditoria,GeradorIds ids,Clock relogio,JsonMapper mapper,ApplicationEventPublisher eventos){
		this.orientacoes=orientacoes;this.fichas=fichas;this.usuario=usuario;this.guarda=guarda;this.auditoria=auditoria;this.ids=ids;this.relogio=relogio;this.mapper=mapper;this.eventos=eventos;
	}

	@Transactional(readOnly=true)
	public OrientacoesResposta listar(UUID paciente){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();
		var ficha=fichas.consultar(paciente,false);
		auditoria.leitura(new ReferenciaClinica("orientacao",ficha.vinculoId(),nutri,ficha.pacienteId(),contexto.organizacaoId()));
		return new OrientacoesResposta(orientacoes.doPacienteDaNutri(ficha.pacienteId(),nutri));
	}

	@Transactional
	public OrientacaoResposta criar(UUID paciente,OrientacaoRequisicao r){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();guarda.validar();orientacoes.bloquear(nutri);
		var ficha=fichas.consultar(paciente,true);
		if(ficha.status()==StatusVinculo.ENCERRADO)throw new ExcecaoNegocio(CodigoErro.ACESSO_NEGADO,"RN-VIN-05");
		String titulo=limparTitulo(r.titulo());String texto=texto(r.texto());
		UUID id=r.id()==null?ids.novo():idV7(r.id());
		if(!orientacoes.inserir(id,ficha.vinculoId(),nutri,ficha.pacienteId(),titulo,texto,relogio.instant()))throw new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02");
		var criada=orientacoes.daNutri(id,nutri,false);auditoria.escrita(referencia(criada,contexto));
		eventos.publishEvent(new OrientacaoCriada(criada.id(),criada.pacienteId()));
		return criada;
	}

	@Transactional
	public OrientacaoResposta atualizar(UUID id,String ifMatch,AtualizarOrientacaoRequisicao r){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();guarda.validar();orientacoes.bloquear(nutri);
		var atual=orientacoes.daNutri(id,nutri,true);
		if(atual==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		var ficha=fichas.consultar(atual.pacienteId(),true);
		if(ficha.status()==StatusVinculo.ENCERRADO)throw new ExcecaoNegocio(CodigoErro.ACESSO_NEGADO,"RN-VIN-05");
		if(versao(ifMatch)!=atual.versao()){
			auditoria.leitura(referencia(atual,contexto));
			throw new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02","A orientação foi atualizada. Revise os dados atuais.",List.of(),mapper.valueToTree(atual));
		}
		boolean arquivar=Boolean.TRUE.equals(r.arquivar());boolean edita=r.titulo()!=null||r.texto()!=null;
		if(arquivar==edita)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-ORI-01"); // ou arquiva, ou edita
		orientacoes.atualizar(id,nutri,r.titulo()!=null?limparTitulo(r.titulo()):atual.titulo(),r.texto()!=null?texto(r.texto()):atual.texto(),arquivar,relogio.instant());
		var nova=orientacoes.daNutri(id,nutri,false);auditoria.escrita(referencia(nova,contexto));return nova;
	}

	static String limparTitulo(String t){
		String l=MarkdownRestrito.limpar(t).replace('\n',' ').strip();
		if(l.isEmpty()||l.length()>120)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-ORI-01");
		return l;
	}
	static String texto(String t){
		String l=MarkdownRestrito.limpar(t);
		if(l.isEmpty()||l.length()>5000)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-ORI-01");
		return l;
	}
	private UUID idV7(UUID id){if(id.version()!=7)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,null);return id;}
	static long versao(String valor){
		if(valor!=null&&valor.matches("\"[0-9]+\""))try{return Long.parseLong(valor.substring(1,valor.length()-1));}catch(NumberFormatException ignorada){}
		throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-SYN-02");
	}
	private ReferenciaClinica referencia(OrientacaoResposta o,ContextoUsuario contexto){return new ReferenciaClinica("orientacao",o.id(),contexto.exigirNutricionista(),o.pacienteId(),contexto.organizacaoId());}
}
