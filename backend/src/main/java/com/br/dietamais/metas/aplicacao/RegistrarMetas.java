package com.br.dietamais.metas.aplicacao;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.metas.api.*;
import com.br.dietamais.metas.infra.MetaStore;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RN-MET-02: o paciente registra o que fez em cada meta; só inclusão, sem conflito, com as mesmas janelas de data do check-in. */
@Service
public class RegistrarMetas {
	static final int DIAS_RETROATIVOS=3;
	static final BigDecimal VALOR_MAXIMO=new BigDecimal("1000000");
	static final BigDecimal AGUA_MAXIMO_ML=new BigDecimal("5000");
	private final MetaStore metas;private final UsuarioAtual usuario;private final GeradorIds ids;private final Clock relogio;
	public RegistrarMetas(MetaStore metas,UsuarioAtual usuario,GeradorIds ids,Clock relogio){this.metas=metas;this.usuario=usuario;this.ids=ids;this.relogio=relogio;}

	@Transactional
	public RegistroMetaResposta registrar(RegistroMetaRequisicao r){
		var contexto=usuario.obter();var paciente=contexto.exigirPaciente();
		UUID id=r.id()==null?ids.novo():r.id();
		if(id.version()!=7)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,null);
		var meta=metas.doPacienteAtiva(r.metaId(),paciente);
		if(meta==null)throw new ExcecaoNegocio(CodigoErro.RECURSO_NAO_ENCONTRADO,null);
		var hoje=LocalDate.now(relogio.withZone(ZoneId.of(contexto.usuario().fuso())));
		if(r.data().isAfter(hoje.plusDays(1))||r.data().isBefore(hoje.minusDays(DIAS_RETROATIVOS))||r.registradoEm().isAfter(relogio.instant().plusSeconds(300)))throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-MET-02");
		if(meta.inicio()!=null&&r.data().isBefore(meta.inicio())||meta.fim()!=null&&r.data().isAfter(meta.fim()))throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-MET-02");
		var valor=r.valor();
		if(valor.compareTo(VALOR_MAXIMO)>0||valor.stripTrailingZeros().scale()>2)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-MET-02");
		if(meta.tipo()==TipoMeta.SIM_NAO&&valor.compareTo(BigDecimal.ZERO)!=0&&valor.compareTo(BigDecimal.ONE)!=0)throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-MET-02");
		// RN-MET-05: o registro da água também vai para registro_agua (ml inteiro de 1 a 5.000), para o app 1.3.x e a rotina verem o mesmo total
		if(meta.tipo()==TipoMeta.AGUA&&(valor.stripTrailingZeros().scale()>0||valor.compareTo(BigDecimal.ONE)<0||valor.compareTo(AGUA_MAXIMO_ML)>0))throw new ExcecaoNegocio(CodigoErro.FORMATO_INVALIDO,"RN-MET-05");
		if(!metas.inserirRegistro(id,meta.id(),paciente,r.data(),valor,r.registradoEm())&&metas.registro(id,paciente)==null)throw new ExcecaoNegocio(CodigoErro.CONFLITO_VERSAO,"RN-SYN-02");
		return metas.registro(id,paciente);
	}
}
