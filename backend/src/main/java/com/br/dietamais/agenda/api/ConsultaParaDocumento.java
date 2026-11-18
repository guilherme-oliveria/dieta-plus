package com.br.dietamais.agenda.api;

import java.util.Optional;
import java.util.UUID;

/** BE-78 (RN-DOC-02): consulta da própria nutri para preencher a declaração de comparecimento. Vazio se não for da nutri. */
public interface ConsultaParaDocumento {
	Optional<ConsultaResposta> daNutri(UUID consultaId,UUID nutri);
}
