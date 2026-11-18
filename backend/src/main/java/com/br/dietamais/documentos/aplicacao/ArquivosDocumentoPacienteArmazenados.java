package com.br.dietamais.documentos.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.documentos.api.ArquivosDocumentoPaciente;
import com.br.dietamais.documentos.api.TipoDocumento;
import com.br.dietamais.documentos.infra.DocumentoStore;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Lê do armazenamento os PDFs já emitidos (o documento é imutável, RN-DOC-02); não gera de novo. Cada leitura vai para o log_acesso. */
@Component
public class ArquivosDocumentoPacienteArmazenados implements ArquivosDocumentoPaciente {
	private static final DateTimeFormatter DATA=DateTimeFormatter.ISO_LOCAL_DATE;
	private final DocumentoStore documentos;private final Armazenamento armazenamento;private final UsuarioAtual usuario;private final AuditoriaClinica auditoria;
	public ArquivosDocumentoPacienteArmazenados(DocumentoStore documentos,Armazenamento armazenamento,UsuarioAtual usuario,AuditoriaClinica auditoria){
		this.documentos=documentos;this.armazenamento=armazenamento;this.usuario=usuario;this.auditoria=auditoria;
	}
	@Override public List<ArquivoDocumento> doPaciente(UUID paciente){
		var organizacao=ContextoSegundoPlano.atual().map(ContextoUsuario::organizacaoId).orElseGet(()->usuario.obter().organizacaoId());
		var arquivos=new ArrayList<ArquivoDocumento>();
		for(var m:documentos.metadadosDoPaciente(paciente)){
			if(m.pdfChave()==null)continue;
			var bytes=armazenamento.ler(m.pdfChave());
			if(bytes.isEmpty())continue;
			auditoria.leitura(new ReferenciaClinica("documento",m.resposta().id(),m.nutri(),m.paciente(),organizacao));
			var d=m.resposta();
			String nome=(d.tipo()==TipoDocumento.ATESTADO?"atestado":"declaracao-comparecimento")+"-"+DATA.format(d.emitidoEm().atZone(ZoneOffset.UTC))
				+"-"+d.id().toString().substring(0,8)+(Boolean.TRUE.equals(d.anulado())?"-anulado":"")+".pdf";
			arquivos.add(new ArquivoDocumento(nome,bytes.get()));
		}
		return arquivos;
	}
}
