package com.br.dietamais.documentos.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.documentos.api.DocumentoPacienteResposta;
import com.br.dietamais.documentos.infra.DocumentoStore;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Coleção `documentos` do sync (BE-79): só metadados (tipo, título, data, anulado), nunca o texto nem o motivo da anulação.
 * A nutri e o paciente puxam; o paciente continua recebendo com o vínculo encerrado ou a assinatura da nutri inativa (RN-ASS-05).
 */
@Component
public class FonteDocumentosSync implements FonteSync<DocumentoPacienteResposta> {
	private final DiarioSync diario;private final DocumentoStore documentos;
	public FonteDocumentosSync(DiarioSync diario,DocumentoStore documentos){this.diario=diario;this.documentos=documentos;}
	@Override public ColecaoSync colecao(){return ColecaoSync.DOCUMENTOS;}
	@Override public List<ReferenciaSync> mudancasDesde(long seq,ContextoUsuario usuario,int limite){return diario.mudancasDesde(colecao(),seq,usuario,limite);}
	@Override public DocumentoPacienteResposta carregar(ReferenciaSync referencia,ContextoUsuario usuario){
		var m=usuario.perfil()==Perfil.NUTRICIONISTA?documentos.metadadosDaNutri(referencia.id(),usuario.exigirNutricionista()):documentos.metadadosDoPaciente(referencia.id(),usuario.exigirPaciente());
		return m==null?null:m.resposta();
	}
}
