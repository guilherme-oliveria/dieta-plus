package com.br.dietamais.documentos.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.documentos.api.*;
import com.br.dietamais.documentos.dominio.PreenchimentoDocumento;
import com.br.dietamais.documentos.infra.ModeloDocumentoStore;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** RN-DOC-01: os dois modelos do sistema são só leitura (a nutri copia criando um novo); os da nutri ficam isolados por nutri (online). */
@Service
public class GerenciarModelosDocumento {
	static final int LIMITE=50;
	private final ModeloDocumentoStore modelos;private final UsuarioAtual usuario;private final GuardaEscritaNutri guarda;private final GeradorIds ids;private final Clock relogio;private final JsonMapper mapper;
	public GerenciarModelosDocumento(ModeloDocumentoStore modelos,UsuarioAtual usuario,GuardaEscritaNutri guarda,GeradorIds ids,Clock relogio,JsonMapper mapper){
		this.modelos=modelos;this.usuario=usuario;this.guarda=guarda;this.ids=ids;this.relogio=relogio;this.mapper=mapper;
	}
	@Transactional(readOnly=true)
	public ModelosDocumentoResposta listar(){return new ModelosDocumentoResposta(modelos.visiveis(usuario.obter().exigirNutricionista()));}

	@Transactional
	public ModeloDocumentoResposta criar(ModeloDocumentoRequisicao r){
		var nutri=usuario.obter().exigirNutricionista();guarda.validar();modelos.bloquear(nutri);
		if(modelos.contar(nutri)>=LIMITE)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-DOC-01","Limite de "+LIMITE+" modelos de documento.",List.of(),null);
		UUID id=ids.novo();
		modelos.inserir(id,nutri,r.tipo(),nome(r.nome()),texto(r.texto()),relogio.instant());
		return modelos.visivel(id,nutri,false);
	}
	@Transactional
	public ModeloDocumentoResposta atualizar(UUID id,String ifMatch,ModeloDocumentoRequisicao r){
		var nutri=usuario.obter().exigirNutricionista();guarda.validar();modelos.bloquear(nutri);
		var atual=proprio(id,nutri);long versao=versao(ifMatch);
		if(versao!=atual.versao()||!modelos.atualizar(id,nutri,versao,r.tipo(),nome(r.nome()),texto(r.texto()),relogio.instant()))throw conflito(atual);
		return modelos.visivel(id,nutri,false);
	}
	@Transactional
	public void excluir(UUID id,String ifMatch){
		var nutri=usuario.obter().exigirNutricionista();guarda.validar();modelos.bloquear(nutri);
		var atual=proprio(id,nutri);long versao=versao(ifMatch);
		if(versao!=atual.versao()||!modelos.excluir(id,nutri,versao,relogio.instant()))throw conflito(atual);
	}

	private ModeloDocumentoResposta proprio(UUID id,UUID nutri){
		var m=modelos.visivel(id,nutri,true);
		if(m==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		if(m.doSistema())throw new ExcecaoNegocio(CodigoErro.ACESSO_NEGADO,"RN-DOC-01","Modelo do sistema não muda. Copie para editar.",List.of(),null);
		return m;
	}
	private ExcecaoNegocio conflito(ModeloDocumentoResposta atual){
		return new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02","O modelo foi atualizado. Revise os dados atuais.",List.of(),mapper.valueToTree(atual));
	}
	static String nome(String n){
		String l=PreenchimentoDocumento.limpar(n).replace('\n',' ');
		if(l.isEmpty()||l.length()>80)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-DOC-01");
		return l;
	}
	static String texto(String t){
		String l=PreenchimentoDocumento.limpar(t);
		if(l.isEmpty()||l.length()>5000)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-DOC-01");
		return l;
	}
	static long versao(String valor){
		if(valor!=null&&valor.matches("\"[0-9]+\""))try{return Long.parseLong(valor.substring(1,valor.length()-1));}catch(NumberFormatException ignorada){}
		throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-SYN-02");
	}
}
