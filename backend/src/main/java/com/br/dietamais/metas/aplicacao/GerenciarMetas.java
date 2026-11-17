package com.br.dietamais.metas.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.metas.api.*;
import com.br.dietamais.metas.dominio.CalculoCumprimento;
import com.br.dietamais.metas.infra.MetaStore;
import com.br.dietamais.metas.infra.MetaStore.Dados;
import com.br.dietamais.pacientes.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** RN-MET-01..03: a nutri cria, ajusta e encerra metas de hábito dos pacientes vinculados (online) e vê o cumprimento; o paciente só lê. */
@Service
public class GerenciarMetas {
	static final int DIAS_MAXIMOS=366;
	private final MetaStore metas;private final ConsultarFichaParaLista fichas;private final UsuarioAtual usuario;private final GuardaEscritaNutri guarda;
	private final AuditoriaClinica auditoria;private final GeradorIds ids;private final Clock relogio;private final JsonMapper mapper;
	public GerenciarMetas(MetaStore metas,ConsultarFichaParaLista fichas,UsuarioAtual usuario,GuardaEscritaNutri guarda,AuditoriaClinica auditoria,GeradorIds ids,Clock relogio,JsonMapper mapper){
		this.metas=metas;this.fichas=fichas;this.usuario=usuario;this.guarda=guarda;this.auditoria=auditoria;this.ids=ids;this.relogio=relogio;this.mapper=mapper;
	}

	@Transactional(readOnly=true)
	public MetasResposta listar(UUID paciente){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();
		var ficha=fichas.consultar(paciente,false);
		auditoria.leitura(new ReferenciaClinica("meta_habito",ficha.vinculoId(),nutri,ficha.pacienteId(),contexto.organizacaoId()));
		return new MetasResposta(metas.doPacienteDaNutri(ficha.pacienteId(),nutri));
	}

	@Transactional
	public MetaResposta criar(UUID paciente,MetaRequisicao r){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();guarda.validar();metas.bloquear(nutri);
		var ficha=fichas.consultar(paciente,true);
		if(ficha.status()==StatusVinculo.ENCERRADO)throw new ExcecaoNegocio(CodigoErro.ACESSO_NEGADO,"RN-VIN-05");
		if(r.tipo()==TipoMeta.AGUA)throw invalida(); // a meta de água existe para todo paciente e não se cria à mão (RN-MET-05)
		UUID id=r.id()==null?ids.novo():idV7(r.id());
		var dados=validar(new Dados(r.tipo(),limpar(r.nome()),r.alvo(),limpar(r.unidade()),r.frequencia(),r.vezesPorSemana(),r.inicio(),r.fim()));
		if(!metas.inserir(id,ficha.vinculoId(),nutri,ficha.pacienteId(),dados,relogio.instant()))throw new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02");
		var criada=propria(id,nutri,false);auditoria.escrita(referencia(criada,contexto));return criada;
	}

	@Transactional
	public MetaResposta atualizar(UUID id,String ifMatch,AtualizarMetaRequisicao r){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();guarda.validar();metas.bloquear(nutri);
		var atual=propria(id,nutri,true);
		var ficha=fichas.consultar(atual.pacienteId(),true);
		if(ficha.status()==StatusVinculo.ENCERRADO)throw new ExcecaoNegocio(CodigoErro.ACESSO_NEGADO,"RN-VIN-05");
		if(versao(ifMatch)!=atual.versao()){
			auditoria.leitura(referencia(atual,contexto));
			throw new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02","A meta foi atualizada. Revise os dados atuais.",List.of(),mapper.valueToTree(atual));
		}
		boolean encerrar=Boolean.TRUE.equals(r.encerrar());
		boolean muda=r.nome()!=null||r.alvo()!=null||r.unidade()!=null||r.frequencia()!=null||r.vezesPorSemana()!=null||r.inicio()!=null||r.fim()!=null;
		if(!encerrar&&!muda)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-MET-01");
		if(encerrar&&muda)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-MET-01");
		if(atual.tipo()==TipoMeta.AGUA&&encerrar)throw new ExcecaoNegocio(CodigoErro.META_INVALIDA,"RN-MET-01"); // a meta de água não se encerra (RN-MET-05)
		var hoje=LocalDate.now(relogio.withZone(ZoneId.of(contexto.usuario().fuso())));
		var frequencia=r.frequencia()!=null?r.frequencia():atual.frequencia();
		Integer vezes=r.vezesPorSemana()!=null?r.vezesPorSemana():frequencia==FrequenciaMeta.SEMANAL?atual.vezesPorSemana():null;
		var fim=encerrar?hoje:r.fim()!=null?r.fim():atual.fim();
		var dados=validar(new Dados(atual.tipo(),r.nome()!=null?limpar(r.nome()):atual.nome(),r.alvo()!=null?r.alvo():atual.alvo(),r.unidade()!=null?limpar(r.unidade()):atual.unidade(),
			frequencia,vezes,r.inicio()!=null?r.inicio():atual.inicio(),fim));
		metas.atualizar(id,nutri,dados,relogio.instant());
		var nova=propria(id,nutri,false);auditoria.escrita(referencia(nova,contexto));return nova;
	}

	/** Cumprimento de cada meta que se sobrepõe à janela; padrão: últimos 7 dias até hoje (fuso da nutri). */
	@Transactional(readOnly=true)
	public CumprimentoMetasResposta cumprimento(UUID paciente,LocalDate de,LocalDate ate){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();
		var hoje=LocalDate.now(relogio.withZone(ZoneId.of(contexto.usuario().fuso())));
		var fim=ate!=null?ate:hoje;var inicio=de!=null?de:fim.minusDays(6);
		if(fim.isBefore(inicio)||ChronoUnit.DAYS.between(inicio,fim)+1>DIAS_MAXIMOS)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-MET-03");
		var ficha=fichas.consultar(paciente,false);
		var lista=metas.daJanela(ficha.pacienteId(),nutri,inicio,fim);
		var somas=metas.somasPorDia(lista.stream().map(MetaResposta::id).toList(),inicio,fim);
		var itens=new ArrayList<CumprimentoMeta>();
		for(var m:lista){
			var c=CalculoCumprimento.calcular(m.frequencia(),m.alvo(),m.vezesPorSemana(),m.inicio(),m.fim(),inicio,fim,hoje,somas.getOrDefault(m.id(),Map.of()));
			itens.add(new CumprimentoMeta(m.id(),m.tipo(),m.nome(),c.diasPrevistos(),c.diasCumpridos(),c.percentual(),c.sequenciaAtual(),c.melhorSequencia()));
		}
		auditoria.leitura(new ReferenciaClinica("meta_habito",ficha.vinculoId(),nutri,ficha.pacienteId(),contexto.organizacaoId()));
		return new CumprimentoMetasResposta(inicio,fim,itens);
	}

	/** RN-MET-01: nome e unidade só em SIM_NAO e CONTADOR (nome obrigatório); SIM_NAO tem alvo 1; SEMANAL pede N de 1 a 7; AGUA só nasce com o paciente (RN-MET-05). */
	static Dados validar(Dados d){
		boolean livre=d.tipo()==TipoMeta.SIM_NAO||d.tipo()==TipoMeta.CONTADOR;
		if(d.tipo()==TipoMeta.AGUA&&d.nome()!=null||!livre&&(d.nome()!=null||d.unidade()!=null))throw invalida();
		if(livre&&d.nome()==null)throw invalida();
		if(d.tipo()==TipoMeta.SIM_NAO&&d.alvo().compareTo(BigDecimal.ONE)!=0)throw invalida();
		if(d.alvo().signum()<=0||d.alvo().scale()>2&&d.alvo().stripTrailingZeros().scale()>2||d.alvo().compareTo(new BigDecimal("1000000"))>0)throw invalida();
		if(d.frequencia()==FrequenciaMeta.SEMANAL&&(d.vezesPorSemana()==null||d.vezesPorSemana()<1||d.vezesPorSemana()>7))throw invalida();
		if(d.frequencia()==FrequenciaMeta.DIARIA&&d.vezesPorSemana()!=null)throw invalida();
		if(d.inicio()!=null&&d.fim()!=null&&d.fim().isBefore(d.inicio()))throw invalida();
		return d;
	}
	private static ExcecaoNegocio invalida(){return new ExcecaoNegocio(CodigoErro.META_INVALIDA,"RN-MET-01");}
	private MetaResposta propria(UUID id,UUID nutri,boolean bloquear){
		var m=metas.daNutri(id,nutri,bloquear);if(m==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);return m;
	}
	private static String limpar(String t){return t==null||t.isBlank()?null:t.strip();}
	private UUID idV7(UUID id){if(id.version()!=7)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,null);return id;}
	private static long versao(String valor){
		if(valor!=null&&valor.matches("\"[0-9]+\""))try{return Long.parseLong(valor.substring(1,valor.length()-1));}catch(NumberFormatException ignorada){}
		throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-SYN-02");
	}
	private ReferenciaClinica referencia(MetaResposta m,ContextoUsuario contexto){return new ReferenciaClinica("meta_habito",m.id(),contexto.exigirNutricionista(),m.pacienteId(),contexto.organizacaoId());}
}
