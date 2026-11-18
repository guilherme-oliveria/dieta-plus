package com.br.dietamais.documentos.api;

import java.util.List;
import java.util.UUID;

/**
 * PDFs dos documentos que o paciente recebeu, para a exportação de dados (RN-LGPD-02, BE-84). Os anulados entram também, com
 * "anulado" no nome, porque o paciente os recebeu.
 */
public interface ArquivosDocumentoPaciente {
	record ArquivoDocumento(String nome,byte[] conteudo) {
		@Override public String toString(){return "ArquivoDocumento[protegido]";}
	}
	List<ArquivoDocumento> doPaciente(UUID paciente);
}
