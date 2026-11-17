package com.br.dietamais.prescricoes.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.prescricoes.api.*;
import com.br.dietamais.prescricoes.infra.ModeloOrientacaoStore;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** RN-ORI-01: modelos reutilizáveis de orientação, da nutri e isolados por nutri (online). */
@Service
public class GerenciarModelosOrientacao {
	static final int LIMITE=100;
	private final ModeloOrientacaoStore modelos;private final UsuarioAtual usuario;private final GuardaEscritaNutri guarda;private final GeradorIds ids;private final Clock relogio;private final JsonMapper mapper;
	public GerenciarModelosOrientacao(ModeloOrientacaoStore modelos,UsuarioAtual usuario,GuardaEscritaNutri guarda,GeradorIds ids,Clock relogio,JsonMapper mapper){
		this.modelos=modelos;this.usuario=usuario;this.guarda=guarda;this.ids=ids;this.relogio=relogio;this.mapper=mapper;
	}
	@Transactional(readOnly=true)
	public ModelosOrientacaoResposta listar(){return new ModelosOrientacaoResposta(modelos.daNutri(usuario.obter().exigirNutricionista()));}

	@Transactional
	public ModeloOrientacaoResposta criar(ModeloOrientacaoRequisicao r){
		var nutri=usuario.obter().exigirNutricionista();guarda.validar();modelos.bloquear(nutri);
		if(modelos.contar(nutri)>=LIMITE)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-ORI-01");
		UUID id=ids.novo();
		modelos.inserir(id,nutri,GerenciarOrientacoes.limparTitulo(r.titulo()),GerenciarOrientacoes.texto(r.texto()),relogio.instant());
		return modelos.daNutri(id,nutri,false);
	}
	@Transactional
	public ModeloOrientacaoResposta atualizar(UUID id,String ifMatch,ModeloOrientacaoRequisicao r){
		var nutri=usuario.obter().exigirNutricionista();guarda.validar();modelos.bloquear(nutri);
		var atual=propria(id,nutri);long versao=GerenciarOrientacoes.versao(ifMatch);
		if(versao!=atual.versao())throw conflito(atual);
		if(!modelos.atualizar(id,nutri,versao,GerenciarOrientacoes.limparTitulo(r.titulo()),GerenciarOrientacoes.texto(r.texto()),relogio.instant()))throw conflito(atual);
		return modelos.daNutri(id,nutri,false);
	}
	@Transactional
	public void excluir(UUID id,String ifMatch){
		var nutri=usuario.obter().exigirNutricionista();guarda.validar();modelos.bloquear(nutri);
		var atual=propria(id,nutri);long versao=GerenciarOrientacoes.versao(ifMatch);
		if(versao!=atual.versao()||!modelos.excluir(id,nutri,versao,relogio.instant()))throw conflito(atual);
	}
	private ModeloOrientacaoResposta propria(UUID id,UUID nutri){
		var m=modelos.daNutri(id,nutri,true);if(m==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);return m;
	}
	private ExcecaoNegocio conflito(ModeloOrientacaoResposta atual){
		return new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02","O modelo foi atualizado. Revise os dados atuais.",List.of(),mapper.valueToTree(atual));
	}
}
