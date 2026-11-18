package com.br.dietamais.agenda.aplicacao;

import com.br.dietamais.agenda.api.*;
import com.br.dietamais.agenda.infra.ConsultaStore;
import com.br.dietamais.agenda.infra.ConsultaStore.Dados;
import com.br.dietamais.comum.api.*;
import com.br.dietamais.notificacoes.api.*;
import com.br.dietamais.pacientes.api.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** RN-AGE-01: a nutri agenda, remarca e cancela consultas de pacientes vinculados; o paciente só lê. */
@Service
public class GerenciarConsultas {
	/** Tolerância para relógios desajustados no aparelho (mesma das avaliações). */
	static final Duration TOLERANCIA=Duration.ofMinutes(5);
	static final Duration HORIZONTE=Duration.ofDays(730);
	static final Duration JANELA_MAXIMA=Duration.ofDays(366);
	private final ConsultaStore consultas;private final ConsultarFichaParaLista fichas;private final UsuarioAtual usuario;private final GuardaEscritaNutri guarda;
	private final AuditoriaClinica auditoria;private final GeradorIds ids;private final Clock relogio;private final JsonMapper mapper;private final EnfileirarEmail emails;
	public GerenciarConsultas(ConsultaStore consultas,ConsultarFichaParaLista fichas,UsuarioAtual usuario,GuardaEscritaNutri guarda,AuditoriaClinica auditoria,GeradorIds ids,Clock relogio,JsonMapper mapper,EnfileirarEmail emails){
		this.consultas=consultas;this.fichas=fichas;this.usuario=usuario;this.guarda=guarda;this.auditoria=auditoria;this.ids=ids;this.relogio=relogio;this.mapper=mapper;this.emails=emails;
	}

	/** Janela padrão: de ontem até 90 dias adiante. {@code paciente} (id da ficha) restringe a um paciente e confere a posse. */
	@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
	public ConsultasResposta listar(UUID paciente,Instant de,Instant ate){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();var janela=janela(de,ate);
		UUID filtro=null;
		if(paciente!=null){
			var ficha=fichas.consultar(paciente,false);filtro=ficha.pacienteId();
			auditoria.leitura(new ReferenciaClinica("consulta",ficha.vinculoId(),nutri,ficha.pacienteId(),contexto.organizacaoId()));
		}
		return new ConsultasResposta(consultas.daNutri(nutri,filtro,janela[0],janela[1]));
	}

	@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
	public ConsultasResposta minhas(Instant de,Instant ate){
		var paciente=usuario.obter().exigirPaciente();var janela=janela(de,ate);
		return new ConsultasResposta(consultas.doPaciente(paciente,janela[0],janela[1]));
	}

	@Transactional
	public ConsultaResposta criar(CriarConsultaRequisicao r){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();guarda.validar();consultas.bloquear(nutri);
		var ficha=fichas.consultar(r.pacienteId(),true);
		if(ficha.status()==StatusVinculo.ENCERRADO)throw new ExcecaoNegocio(CodigoErro.ACESSO_NEGADO,"RN-VIN-05");
		var agora=relogio.instant();exigirDataValida(r.inicio(),agora);
		UUID id=r.id()==null?ids.novo():idV7(r.id());
		// C-02: o tipo legado ONLINE vira RETORNO + modalidade ONLINE
		var tipo=r.tipo()==TipoConsulta.ONLINE?TipoConsulta.RETORNO:r.tipo();
		var modalidade=r.modalidade()!=null?r.modalidade():r.tipo()==TipoConsulta.ONLINE?ModalidadeConsulta.ONLINE:ModalidadeConsulta.PRESENCIAL;
		var link=link(limpar(r.linkOnline()),modalidade);
		var dados=new Dados(r.inicio(),r.duracaoMin(),tipo,limpar(r.lugar()),StatusConsulta.AGENDADA,modalidade,link,limpar(r.orientacaoPaciente()),limpar(r.observacaoInterna()));
		if(!consultas.inserir(id,ficha.vinculoId(),nutri,ficha.pacienteId(),dados,agora))throw new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02");
		var criada=propria(id,nutri,false);auditoria.escrita(referencia(criada,contexto));
		confirmarPorEmail(criada,agora);
		return criada;
	}

	@Transactional
	public ConsultaResposta atualizar(UUID id,String ifMatch,AtualizarConsultaRequisicao r){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();guarda.validar();consultas.bloquear(nutri);
		var atual=propria(id,nutri,true);
		var ficha=fichas.consultar(atual.pacienteId(),true);
		if(ficha.status()==StatusVinculo.ENCERRADO)throw new ExcecaoNegocio(CodigoErro.ACESSO_NEGADO,"RN-VIN-05");
		if(versao(ifMatch)!=atual.versao()){
			auditoria.leitura(referencia(atual,contexto));
			throw new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02","A consulta foi atualizada. Revise os dados atuais.",List.of(),mapper.valueToTree(atual));
		}
		if(atual.status()==StatusConsulta.CANCELADA)throw new ExcecaoNegocio(CodigoErro.CONSULTA_CANCELADA,"RN-AGE-01");
		boolean mudaAgenda=r.inicio()!=null||r.duracaoMin()!=null||r.tipo()!=null||r.lugar()!=null||r.modalidade()!=null||r.linkOnline()!=null;
		boolean mudaTextos=r.orientacaoPaciente()!=null||r.observacaoInterna()!=null;
		if(r.status()==null&&!mudaAgenda&&!mudaTextos)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-AGE-01");
		if(r.status()!=null&&r.status()==atual.status())throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-AGE-03"); // repetir o status atual não é mudança
		var destino=r.status()==null?atual.status():r.status();
		if(destino!=atual.status())exigirTransicao(atual.status(),destino);
		// RN-AGE-03: remarcar só enquanto AGENDADA e sem mudar de status na mesma chamada
		if(mudaAgenda&&(atual.status()!=StatusConsulta.AGENDADA||destino!=StatusConsulta.AGENDADA))throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-AGE-03");
		var agora=relogio.instant();
		if(r.inicio()!=null)exigirDataValida(r.inicio(),agora);
		var tipo=r.tipo()==TipoConsulta.ONLINE?TipoConsulta.RETORNO:r.tipo()!=null?r.tipo():atual.tipo();
		var modalidade=r.modalidade()!=null?r.modalidade():r.tipo()==TipoConsulta.ONLINE?ModalidadeConsulta.ONLINE:atual.modalidade();
		var linkAtual=modalidade==ModalidadeConsulta.ONLINE?atual.linkOnline():null;
		var link=link(r.linkOnline()!=null?limpar(r.linkOnline()):linkAtual,modalidade);
		var dados=new Dados(r.inicio()!=null?r.inicio():atual.inicio(),r.duracaoMin()!=null?r.duracaoMin():atual.duracaoMin(),tipo,
			r.lugar()!=null?limpar(r.lugar()):atual.lugar(),destino,modalidade,link,
			r.orientacaoPaciente()!=null?limpar(r.orientacaoPaciente()):atual.orientacaoPaciente(),r.observacaoInterna()!=null?limpar(r.observacaoInterna()):atual.observacaoInterna());
		consultas.atualizar(id,nutri,dados,agora);
		var nova=propria(id,nutri,false);auditoria.escrita(referencia(nova,contexto));return nova;
	}

	/**
	 * BE-72 (RN-AGE-02, C-10): e-mail de confirmação ao agendar, com data, hora e, na consulta online, o link da sala.
	 * Vai na mesma transação (fila cifrada); expira no início da consulta. Consulta já começando não gera e-mail.
	 */
	private void confirmarPorEmail(ConsultaResposta c,Instant agora){
		if(!c.inicio().isAfter(agora))return;
		consultas.emailParaConfirmacao(c.pacienteId()).ifPresent(email->
			emails.enfileirar(new EmailTransacional(TipoEmail.CONSULTA_AGENDADA,email,null,c.inicio(),c.inicio(),c.linkOnline())));
	}

	/** [de, ate): padrão de ontem até 90 dias adiante; no máximo 366 dias por consulta. */
	private Instant[] janela(Instant de,Instant ate){
		var inicio=de!=null?de:relogio.instant().minus(Duration.ofDays(1));
		var fim=ate!=null?ate:inicio.plus(Duration.ofDays(90));
		if(!fim.isAfter(inicio)||Duration.between(inicio,fim).compareTo(JANELA_MAXIMA)>0)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-AGE-01");
		return new Instant[]{inicio,fim};
	}
	private ConsultaResposta propria(UUID id,UUID nutri,boolean bloquear){
		var c=consultas.daNutri(id,nutri,bloquear);if(c==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);return c;
	}
	private void exigirDataValida(Instant inicio,Instant agora){
		if(inicio.isBefore(agora.minus(TOLERANCIA))||inicio.isAfter(agora.plus(HORIZONTE)))throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-AGE-01");
	}
	/** RN-AGE-03: AGENDADA vai para REALIZADA, FALTA ou CANCELADA; REALIZADA e FALTA se corrigem entre si; CANCELADA é final; ENCERRADA nunca se grava. */
	static void exigirTransicao(StatusConsulta de,StatusConsulta para){
		boolean ok=switch(de){
			case AGENDADA->para==StatusConsulta.REALIZADA||para==StatusConsulta.FALTA||para==StatusConsulta.CANCELADA;
			case REALIZADA->para==StatusConsulta.FALTA;
			case FALTA->para==StatusConsulta.REALIZADA;
			case CANCELADA,ENCERRADA->false;
		};
		if(!ok)throw new ExcecaoNegocio(CodigoErro.TRANSICAO_STATUS_INVALIDA,"RN-AGE-03");
	}
	/** RN-AGE-04: só consulta ONLINE tem link, e só https com até 300 caracteres. */
	private static String link(String valor,ModalidadeConsulta modalidade){
		if(valor==null)return null;
		if(modalidade!=ModalidadeConsulta.ONLINE||valor.length()>300)throw new ExcecaoNegocio(CodigoErro.LINK_INVALIDO,"RN-AGE-04");
		try{var uri=java.net.URI.create(valor);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getHost().isBlank())throw new ExcecaoNegocio(CodigoErro.LINK_INVALIDO,"RN-AGE-04");}
		catch(IllegalArgumentException e){throw new ExcecaoNegocio(CodigoErro.LINK_INVALIDO,"RN-AGE-04");}
		return valor;
	}
	private static String limpar(String lugar){return lugar==null||lugar.isBlank()?null:lugar.strip();}
	private UUID idV7(UUID id){if(id.version()!=7)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,null);return id;}
	private static long versao(String valor){
		if(valor!=null&&valor.matches("\"[0-9]+\""))try{return Long.parseLong(valor.substring(1,valor.length()-1));}catch(NumberFormatException ignorada){}
		throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-SYN-02");
	}
	private ReferenciaClinica referencia(ConsultaResposta c,ContextoUsuario contexto){return new ReferenciaClinica("consulta",c.id(),contexto.exigirNutricionista(),c.pacienteId(),contexto.organizacaoId());}
}
