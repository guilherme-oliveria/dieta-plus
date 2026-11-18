package com.br.dietamais.documentos.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.documentos.api.*;
import com.br.dietamais.documentos.infra.DocumentoStore;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE-79 (RN-DOC-02): o paciente lista os documentos que recebeu (só metadados) e baixa o PDF gravado na emissão.
 * Vale também com o vínculo encerrado ou a assinatura da nutri inativa (RN-ASS-05). Anulado continua listado e marcado.
 */
@Service
public class DocumentosPaciente {
	private static final DateTimeFormatter DATA=DateTimeFormatter.ofPattern("dd-MM-yyyy");
	private final DocumentoStore documentos;private final Armazenamento armazenamento;private final UsuarioAtual usuario;private final AuditoriaClinica auditoria;
	public DocumentosPaciente(DocumentoStore documentos,Armazenamento armazenamento,UsuarioAtual usuario,AuditoriaClinica auditoria){
		this.documentos=documentos;this.armazenamento=armazenamento;this.usuario=usuario;this.auditoria=auditoria;
	}

	@Transactional(readOnly=true)
	public DocumentosPacienteResposta listar(){
		var paciente=usuario.obter().exigirPaciente();
		return new DocumentosPacienteResposta(documentos.doPaciente(paciente));
	}

	@Transactional(readOnly=true)
	public PdfResposta pdf(UUID id){
		var contexto=usuario.obter();var paciente=contexto.exigirPaciente();
		var m=documentos.metadadosDoPaciente(id,paciente);
		if(m==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		auditoria.leitura(new ReferenciaClinica("documento",id,m.nutri(),m.paciente(),contexto.organizacaoId()));
		var d=m.resposta();
		String nome=(d.tipo()==TipoDocumento.ATESTADO?"atestado":"declaracao-comparecimento")+"-"+DATA.format(d.emitidoEm().atZone(ZoneOffset.UTC))+".pdf";
		var acesso=armazenamento.urlAssinada(m.pdfChave(),GerenciarDocumentos.VALIDADE_LINK,nome,"application/pdf");
		return new PdfResposta(acesso.url(),acesso.expiraEm());
	}
}
