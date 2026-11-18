package com.br.dietamais.agenda.aplicacao;

import com.br.dietamais.agenda.api.ConsultaParaDocumento;
import com.br.dietamais.agenda.api.ConsultaResposta;
import com.br.dietamais.agenda.infra.ConsultaStore;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConsultaParaDocumentoBanco implements ConsultaParaDocumento {
	private final ConsultaStore store;
	public ConsultaParaDocumentoBanco(ConsultaStore store){this.store=store;}
	@Override @Transactional(readOnly=true)
	public Optional<ConsultaResposta> daNutri(UUID consultaId,UUID nutri){return Optional.ofNullable(store.daNutri(consultaId,nutri,false));}
}
