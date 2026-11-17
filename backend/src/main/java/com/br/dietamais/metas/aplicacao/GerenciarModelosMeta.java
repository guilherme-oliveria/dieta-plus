package com.br.dietamais.metas.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.metas.api.*;
import com.br.dietamais.metas.infra.MetaStore;
import com.br.dietamais.metas.infra.MetaStore.Dados;
import com.br.dietamais.metas.infra.ModeloMetaStore;
import com.br.dietamais.metas.infra.ModeloMetaStore.Linha;
import com.br.dietamais.pacientes.api.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** RN-MET-04: modelos de metas da nutri, isolados por nutri, e a aplicação deles a um paciente (online). */
@Service
public class GerenciarModelosMeta {
	static final int LIMITE_MODELOS=100;
	private final ModeloMetaStore modelos;private final MetaStore metas;private final ConsultarFichaParaLista fichas;private final UsuarioAtual usuario;private final GuardaEscritaNutri guarda;
	private final AuditoriaClinica auditoria;private final GeradorIds ids;private final Clock relogio;private final JsonMapper mapper;
	public GerenciarModelosMeta(ModeloMetaStore modelos,MetaStore metas,ConsultarFichaParaLista fichas,UsuarioAtual usuario,GuardaEscritaNutri guarda,AuditoriaClinica auditoria,GeradorIds ids,Clock relogio,JsonMapper mapper){
		this.modelos=modelos;this.metas=metas;this.fichas=fichas;this.usuario=usuario;this.guarda=guarda;this.auditoria=auditoria;this.ids=ids;this.relogio=relogio;this.mapper=mapper;
	}

	@Transactional(readOnly=true)
	public ModelosMetaResposta listar(){
		var nutri=usuario.obter().exigirNutricionista();
		return new ModelosMetaResposta(modelos.daNutri(nutri).stream().map(this::resposta).toList());
	}
	@Transactional(readOnly=true)
	public ModeloMetaResposta consultar(UUID id){return resposta(propria(id,usuario.obter().exigirNutricionista(),false));}

	@Transactional
	public ModeloMetaResposta criar(ModeloMetaRequisicao r){
		var nutri=usuario.obter().exigirNutricionista();guarda.validar();modelos.bloquear(nutri);
		var itens=validar(r.itens());
		if(modelos.contar(nutri)>=LIMITE_MODELOS)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-MET-04");
		UUID id=ids.novo();
		modelos.inserir(id,nutri,r.nome().strip(),json(itens),relogio.instant());
		return resposta(propria(id,nutri,false));
	}

	@Transactional
	public ModeloMetaResposta atualizar(UUID id,String ifMatch,ModeloMetaRequisicao r){
		var nutri=usuario.obter().exigirNutricionista();guarda.validar();modelos.bloquear(nutri);
		var atual=propria(id,nutri,true);long versao=versao(ifMatch);
		if(versao!=atual.versao())throw conflito(atual);
		var itens=validar(r.itens());
		if(!modelos.atualizar(id,nutri,versao,r.nome().strip(),json(itens),relogio.instant()))throw conflito(atual);
		return resposta(propria(id,nutri,false));
	}

	@Transactional
	public void excluir(UUID id,String ifMatch){
		var nutri=usuario.obter().exigirNutricionista();guarda.validar();modelos.bloquear(nutri);
		var atual=propria(id,nutri,true);long versao=versao(ifMatch);
		if(versao!=atual.versao()||!modelos.excluir(id,nutri,versao,relogio.instant()))throw conflito(atual);
	}

	/** Cria no paciente uma meta por item do modelo, sem prazo; metas já existentes não mudam. */
	@Transactional
	public MetasResposta aplicar(UUID paciente,UUID modeloId){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();guarda.validar();metas.bloquear(nutri);
		var modelo=propria(modeloId,nutri,false);
		var ficha=fichas.consultar(paciente,true);
		if(ficha.status()==StatusVinculo.ENCERRADO)throw new ExcecaoNegocio(CodigoErro.ACESSO_NEGADO,"RN-VIN-05");
		var agora=relogio.instant();var criadas=new ArrayList<MetaResposta>();
		for(var item:itens(modelo)){
			var dados=GerenciarMetas.validar(new Dados(item.tipo(),item.nome(),item.alvo(),item.unidade(),item.frequencia(),item.vezesPorSemana(),null,null));
			UUID id=ids.novo();
			metas.inserir(id,ficha.vinculoId(),nutri,ficha.pacienteId(),dados,agora);
			var criada=metas.daNutri(id,nutri,false);criadas.add(criada);
			auditoria.escrita(new ReferenciaClinica("meta_habito",criada.id(),nutri,criada.pacienteId(),contexto.organizacaoId()));
		}
		return new MetasResposta(criadas);
	}

	/** RN-MET-01 por item; a água nasce com o paciente (RN-MET-05) e por isso não entra em modelo. */
	private List<ItemModeloMeta> validar(List<ItemModeloMeta> itens){
		var limpos=new ArrayList<ItemModeloMeta>();
		for(var i:itens){
			if(i.tipo()==TipoMeta.AGUA)throw new ExcecaoNegocio(CodigoErro.META_INVALIDA,"RN-MET-04");
			var nome=i.nome()==null||i.nome().isBlank()?null:i.nome().strip();var unidade=i.unidade()==null||i.unidade().isBlank()?null:i.unidade().strip();
			var vezes=i.frequencia()==FrequenciaMeta.SEMANAL?i.vezesPorSemana():null;
			GerenciarMetas.validar(new Dados(i.tipo(),nome,i.alvo(),unidade,i.frequencia(),vezes,null,null));
			limpos.add(new ItemModeloMeta(i.tipo(),nome,i.alvo(),unidade,i.frequencia(),vezes));
		}
		return limpos;
	}
	private Linha propria(UUID id,UUID nutri,boolean bloquear){
		var l=modelos.daNutri(id,nutri,bloquear);if(l==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);return l;
	}
	private ExcecaoNegocio conflito(Linha atual){
		return new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02","O modelo foi atualizado. Revise os dados atuais.",List.of(),mapper.valueToTree(resposta(atual)));
	}
	private List<ItemModeloMeta> itens(Linha l){return mapper.readValue(l.itensJson(),new TypeReference<List<ItemModeloMeta>>(){});}
	private String json(List<ItemModeloMeta> itens){return mapper.writeValueAsString(itens);}
	private ModeloMetaResposta resposta(Linha l){return new ModeloMetaResposta(l.id(),l.nome(),itens(l),l.versao(),l.criadoEm(),l.atualizadoEm());}
	private static long versao(String valor){
		if(valor!=null&&valor.matches("\"[0-9]+\""))try{return Long.parseLong(valor.substring(1,valor.length()-1));}catch(NumberFormatException ignorada){}
		throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-SYN-02");
	}
}
