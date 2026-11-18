package com.br.dietamais.documentos.aplicacao;

import com.br.dietamais.agenda.api.ConsultaParaDocumento;
import com.br.dietamais.agenda.api.StatusConsulta;
import com.br.dietamais.comum.api.*;
import com.br.dietamais.documentos.api.*;
import com.br.dietamais.documentos.dominio.CodigoConferencia;
import com.br.dietamais.documentos.dominio.PreenchimentoDocumento;
import com.br.dietamais.documentos.infra.DocumentoStore;
import com.br.dietamais.documentos.infra.ModeloDocumentoStore;
import com.br.dietamais.pacientes.api.*;
import com.br.dietamais.relatorios.api.PdfResposta;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-DOC-02: a nutri emite atestado ou declaração a partir de um modelo (do sistema ou dela). O texto final e o PDF são gravados e nunca mudam;
 * corrigir é emitir outro. Anular exige motivo. Emissão, anulação e leitura ficam na auditoria. Escrita online.
 */
@Service
public class GerenciarDocumentos {
	static final Duration VALIDADE_LINK=Duration.ofMinutes(10);
	static final String PREFIXO="documentos";
	private static final int LIMITE_TEXTO=6000;
	private static final DateTimeFormatter DATA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter HORA=DateTimeFormatter.ofPattern("HH:mm");

	private final DocumentoStore documentos;private final ModeloDocumentoStore modelos;private final ConsultarFichaParaLista fichas;private final ConsultaCabecalhoRelatorio cabecalhos;
	private final ConsultaParaDocumento consultas;private final ConsultaNutricionistas nutricionistas;private final Armazenamento armazenamento;private final UsuarioAtual usuario;
	private final GuardaEscritaNutri guarda;private final AuditoriaClinica auditoria;private final GeradorIds ids;private final Clock relogio;
	private final GeradorPdfDocumento gerador=new GeradorPdfDocumento();private final CodigoConferencia codigos=new CodigoConferencia();

	public GerenciarDocumentos(DocumentoStore documentos,ModeloDocumentoStore modelos,ConsultarFichaParaLista fichas,ConsultaCabecalhoRelatorio cabecalhos,ConsultaParaDocumento consultas,
			ConsultaNutricionistas nutricionistas,Armazenamento armazenamento,UsuarioAtual usuario,GuardaEscritaNutri guarda,AuditoriaClinica auditoria,GeradorIds ids,Clock relogio){
		this.documentos=documentos;this.modelos=modelos;this.fichas=fichas;this.cabecalhos=cabecalhos;this.consultas=consultas;this.nutricionistas=nutricionistas;
		this.armazenamento=armazenamento;this.usuario=usuario;this.guarda=guarda;this.auditoria=auditoria;this.ids=ids;this.relogio=relogio;
	}

	@Transactional(readOnly=true)
	public DocumentosResposta listar(UUID paciente){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();
		var ficha=fichas.consultar(paciente,false);
		auditoria.leitura(new ReferenciaClinica("documento",ficha.vinculoId(),nutri,ficha.pacienteId(),contexto.organizacaoId()));
		return new DocumentosResposta(documentos.doPacienteDaNutri(ficha.pacienteId(),nutri));
	}

	@Transactional
	public DocumentoResposta emitir(UUID paciente,EmitirDocumentoRequisicao r){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();guarda.validar();documentos.bloquear(nutri);
		var ficha=fichas.consultar(paciente,true);
		if(ficha.status()==StatusVinculo.ENCERRADO)throw new ExcecaoNegocio(CodigoErro.ACESSO_NEGADO,"RN-VIN-05");
		UUID id=r.id()==null?ids.novo():idV7(r.id());
		if(documentos.existe(id))throw new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02");
		var modelo=modelos.visivel(r.modeloId(),nutri,false);
		if(modelo==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		var dadosNutri=nutricionistas.porIds(Set.of(nutri)).get(nutri);
		if(dadosNutri==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		ZoneId fuso=ZoneId.of(contexto.usuario().fuso());

		// RN-DOC-02: com consulta, a data e o horário vêm dela, e ela tem de ser REALIZADA, deste paciente e desta nutri
		LocalDate data=r.data();String horario=r.horario();UUID consultaId=null;
		if(r.consultaId()!=null){
			var c=consultas.daNutri(r.consultaId(),nutri).filter(x->x.pacienteId().equals(ficha.pacienteId()))
				.orElseThrow(()->new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null));
			if(c.status()!=StatusConsulta.REALIZADA)
				throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-DOC-02","O documento só parte de consulta realizada.",List.of(),null);
			var inicio=c.inicio().atZone(fuso);data=inicio.toLocalDate();horario=HORA.format(inicio);consultaId=c.id();
		}
		if(data==null)data=LocalDate.now(relogio.withZone(fuso));

		String nomePaciente=cabecalhos.doVinculo(ficha.vinculoId()).map(ConsultaCabecalhoRelatorio.Cabecalho::paciente).orElse(null);
		var valores=new HashMap<String,String>();
		valores.put("paciente",nomePaciente);valores.put("data",DATA.format(data));valores.put("horario",horario);valores.put("nutri",dadosNutri.nome());valores.put("crn",dadosNutri.crn());
		var preenchido=PreenchimentoDocumento.preencher(r.texto()!=null&&!r.texto().isBlank()?r.texto():modelo.texto(),valores);
		if(!preenchido.completo())throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-DOC-01","Faltam dados para preencher: "+String.join(", ",preenchido.faltando())+".",List.of(),null);
		String texto=preenchido.texto();
		if(texto.isEmpty()||texto.length()>LIMITE_TEXTO)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-DOC-01");

		String codigo=codigoLivre();
		Instant agora=relogio.instant();String titulo=titulo(modelo.tipo());
		byte[] pdf=gerador.gerar(new GeradorPdfDocumento.Conteudo(modelo.tipo(),titulo,texto,dadosNutri.nome(),dadosNutri.crn(),DATA.format(agora.atZone(fuso)),codigo,logo(nutri)));
		String chave=PREFIXO+"/"+nutri+"/"+id+".pdf";
		armazenamento.guardar(chave,pdf,"application/pdf");
		var novo=new DocumentoStore.Novo(id,ficha.vinculoId(),nutri,ficha.pacienteId(),modelo.id(),consultaId,modelo.tipo(),titulo,texto,dadosNutri.nome(),dadosNutri.crn(),codigo,chave,agora);
		if(!documentos.inserir(novo)){armazenamento.apagar(chave);throw new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02");}
		var emitido=documentos.daNutri(id,nutri,false).resposta();
		auditoria.escrita(referencia(emitido,contexto));
		return emitido;
	}

	@Transactional
	public DocumentoResposta anular(UUID id,AnularDocumentoRequisicao r){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();guarda.validar();
		var atual=documentos.daNutri(id,nutri,true);
		if(atual==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		if(atual.resposta().anuladoEm()!=null)throw new ExcecaoNegocio(CodigoErro.DOCUMENTO_JA_ANULADO,"RN-DOC-02");
		String motivo=PreenchimentoDocumento.limpar(r.motivo()).replace('\n',' ');
		if(motivo.isEmpty()||motivo.length()>300)throw new ExcecaoNegocio(CodigoErro.OBRIGATORIO,"RN-DOC-02");
		documentos.anular(id,nutri,motivo,relogio.instant());
		var anulado=documentos.daNutri(id,nutri,false).resposta();
		auditoria.escrita(referencia(anulado,contexto));
		return anulado;
	}

	/** Link assinado de 10 min para o PDF gravado na emissão (o mesmo arquivo, sempre). */
	@Transactional(readOnly=true)
	public PdfResposta pdf(UUID id){
		var contexto=usuario.obter();var nutri=contexto.exigirNutricionista();
		var d=documentos.daNutri(id,nutri,false);
		if(d==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		auditoria.leitura(referencia(d.resposta(),contexto));
		var acesso=armazenamento.urlAssinada(d.pdfChave(),VALIDADE_LINK,nomeArquivo(d.resposta()),"application/pdf");
		return new PdfResposta(acesso.url(),acesso.expiraEm());
	}

	static String titulo(TipoDocumento t){return switch(t){case ATESTADO->"Atestado";case DECLARACAO_COMPARECIMENTO->"Declaração de comparecimento";};}
	static String nomeArquivo(DocumentoResposta d){
		return (d.tipo()==TipoDocumento.ATESTADO?"atestado":"declaracao-comparecimento")+"-"+DATA.format(d.emitidoEm().atZone(ZoneOffset.UTC)).replace('/','-')+".pdf";
	}
	private String codigoLivre(){
		for(int i=0;i<5;i++){String c=codigos.novo();if(!documentos.codigoEmUso(c))return c;}
		throw new IllegalStateException("Não foi possível gerar um código de conferência livre");
	}
	private GeradorPdfDocumento.Logo logo(UUID nutri){
		return nutricionistas.logoChave(nutri).flatMap(chave->armazenamento.ler(chave).map(b->new GeradorPdfDocumento.Logo(b,chave.endsWith(".png")?"image/png":"image/jpeg"))).orElse(null);
	}
	private UUID idV7(UUID id){if(id.version()!=7)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,null);return id;}
	private ReferenciaClinica referencia(DocumentoResposta d,ContextoUsuario contexto){return new ReferenciaClinica("documento",d.id(),contexto.exigirNutricionista(),d.pacienteId(),contexto.organizacaoId());}
}
