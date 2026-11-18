package com.br.dietamais.documentos.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.documentos.api.*;
import com.br.dietamais.documentos.dominio.CodigoConferencia;
import com.br.dietamais.documentos.infra.DocumentoStore;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-DOC-03 (BE-79): conferência pública pelo código impresso no PDF. Mostra só tipo, emissor, CRN, data e situação; nunca dado do paciente.
 * Toda tentativa, achada ou não, fica registrada em `conferencia_documento` (sem o código digitado, que não é guardado quando não existe).
 */
@Service
public class ConferirDocumento {
	private final DocumentoStore documentos;private final Clock relogio;
	public ConferirDocumento(DocumentoStore documentos,Clock relogio){this.documentos=documentos;this.relogio=relogio;}

	/** O 404 não desfaz o registro da tentativa. */
	@Transactional(noRollbackFor=ExcecaoNegocio.class)
	public ConferenciaDocumentoResposta conferir(String codigo,String ip){
		String normalizado=CodigoConferencia.normalizar(codigo);
		var c=normalizado==null?null:documentos.porCodigo(normalizado);
		documentos.registrarConferencia(c==null?null:c.id(),c!=null,ip,relogio.instant());
		if(c==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,"RN-DOC-03");
		return new ConferenciaDocumentoResposta(c.tipo(),c.emissor(),c.crn(),c.emitidoEm(),c.anulado()?SituacaoDocumento.ANULADO:SituacaoDocumento.VALIDO);
	}
}
