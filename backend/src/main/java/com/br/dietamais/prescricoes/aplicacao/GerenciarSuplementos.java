package com.br.dietamais.prescricoes.aplicacao;

import com.br.dietamais.alimentos.api.ConsultarProdutosParaDieta;
import com.br.dietamais.comum.api.*;
import com.br.dietamais.pacientes.api.*;
import com.br.dietamais.prescricoes.api.*;
import com.br.dietamais.prescricoes.infra.SuplementoStore;
import com.br.dietamais.prescricoes.infra.SuplementoStore.Dados;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** RN-SUP-01 e 02: a nutri prescreve, ajusta e encerra suplementos dos pacientes vinculados (online); nome e dose ficam congelados na criação. */
@Service
public class GerenciarSuplementos {
	private final SuplementoStore suplementos;private final ConsultarFichaParaLista fichas;private final ConsultarProdutosParaDieta produtos;private final UsuarioAtual usuario;private final GuardaEscritaNutri guarda;
	private final AuditoriaClinica auditoria;private final GeradorIds ids;private final Clock relogio;private final JsonMapper mapper;
	public GerenciarSuplementos(SuplementoStore suplementos,ConsultarFichaParaLista fichas,ConsultarProdutosParaDieta produtos,UsuarioAtual usuario,GuardaEscritaNutri guarda,AuditoriaClinica auditoria,GeradorIds ids,Clock relogio,JsonMapper mapper){
		this.suplementos=suplementos;this.fichas=fichas;this.produtos=produtos;this.usuario=usuario;this.guarda=guarda;this.auditoria=auditoria;this.ids=ids;this.relogio=relogio;this.mapper=mapper;
	}

	@Transactional(readOnly=true)
	public SuplementosResposta listar(UUID paciente){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();
		var ficha=fichas.consultar(paciente,false);
		auditoria.leitura(new ReferenciaClinica("prescricao_suplemento",ficha.vinculoId(),nutri,ficha.pacienteId(),contexto.organizacaoId()));
		return new SuplementosResposta(suplementos.doPacienteDaNutri(ficha.pacienteId(),nutri));
	}

	@Transactional
	public SuplementoResposta criar(UUID paciente,SuplementoRequisicao r){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();guarda.validar();suplementos.bloquear(nutri);
		var ficha=fichas.consultar(paciente,true);
		if(ficha.status()==StatusVinculo.ENCERRADO)throw new ExcecaoNegocio(CodigoErro.ACESSO_NEGADO,"RN-VIN-05");
		boolean temNome=r.nome()!=null&&!r.nome().isBlank();
		if(temNome==(r.produtoId()!=null))throw new ExcecaoNegocio(CodigoErro.ITEM_INVALIDO,"RN-SUP-01"); // produto ou nome, nunca os dois nem nenhum
		String nome=temNome?r.nome().strip():null;
		if(r.produtoId()!=null){
			var produto=produtos.consultar(nutri,List.of(r.produtoId())).get(r.produtoId());
			if(produto==null)throw new ExcecaoNegocio(CodigoErro.ITEM_INVALIDO,"RN-SUP-01");
			nome=produto.nome().length()>120?produto.nome().substring(0,120):produto.nome(); // cópia congelada
		}
		UUID id=r.id()==null?ids.novo():idV7(r.id());
		var dados=validar(new Dados(r.produtoId(),nome,r.dose().strip(),limpar(r.forma()),limpar(r.frequencia()),horarios(r.horarios()),r.inicio(),r.fim()));
		if(!suplementos.inserir(id,ficha.vinculoId(),nutri,ficha.pacienteId(),dados,relogio.instant()))throw new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02");
		var criado=suplementos.daNutri(id,nutri,false);auditoria.escrita(referencia(criado,contexto));return criado;
	}

	@Transactional
	public SuplementoResposta atualizar(UUID id,String ifMatch,AtualizarSuplementoRequisicao r){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();guarda.validar();suplementos.bloquear(nutri);
		var atual=suplementos.daNutri(id,nutri,true);
		if(atual==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		var ficha=fichas.consultar(atual.pacienteId(),true);
		if(ficha.status()==StatusVinculo.ENCERRADO)throw new ExcecaoNegocio(CodigoErro.ACESSO_NEGADO,"RN-VIN-05");
		if(versao(ifMatch)!=atual.versao()){
			auditoria.leitura(referencia(atual,contexto));
			throw new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02","A prescrição foi atualizada. Revise os dados atuais.",List.of(),mapper.valueToTree(atual));
		}
		boolean encerrar=Boolean.TRUE.equals(r.encerrar());
		boolean muda=r.dose()!=null||r.forma()!=null||r.frequencia()!=null||r.horarios()!=null||r.fim()!=null;
		if(encerrar==muda)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-SUP-02"); // ou encerra, ou ajusta
		var hoje=LocalDate.now(relogio.withZone(ZoneId.of(contexto.usuario().fuso())));
		if(r.dose()!=null&&r.dose().isBlank())throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-SUP-02");
		var dados=validar(new Dados(atual.produtoId(),atual.nome(),r.dose()!=null?r.dose().strip():atual.dose(),r.forma()!=null?limpar(r.forma()):atual.forma(),
			r.frequencia()!=null?limpar(r.frequencia()):atual.frequencia(),r.horarios()!=null?horarios(r.horarios()):atual.horarios(),atual.inicio(),encerrar?hoje:r.fim()!=null?r.fim():atual.fim()));
		suplementos.atualizar(id,nutri,dados,relogio.instant());
		var nova=suplementos.daNutri(id,nutri,false);auditoria.escrita(referencia(nova,contexto));return nova;
	}

	private static Dados validar(Dados d){
		if(d.fim()!=null&&d.fim().isBefore(d.inicio()))throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-SUP-02");
		return d;
	}
	private static List<String> horarios(List<String> lista){return lista==null?List.of():lista.stream().distinct().sorted().toList();}
	private static String limpar(String t){return t==null||t.isBlank()?null:t.strip();}
	private UUID idV7(UUID id){if(id.version()!=7)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,null);return id;}
	private static long versao(String valor){
		if(valor!=null&&valor.matches("\"[0-9]+\""))try{return Long.parseLong(valor.substring(1,valor.length()-1));}catch(NumberFormatException ignorada){}
		throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-SYN-02");
	}
	private ReferenciaClinica referencia(SuplementoResposta s,ContextoUsuario contexto){return new ReferenciaClinica("prescricao_suplemento",s.id(),contexto.exigirNutricionista(),s.pacienteId(),contexto.organizacaoId());}
}
