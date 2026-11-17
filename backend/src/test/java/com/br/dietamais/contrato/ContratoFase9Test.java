package com.br.dietamais.contrato;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.br.dietamais.agenda.api.*;
import com.br.dietamais.assinaturas.api.*;
import com.br.dietamais.comum.api.ProblemaResposta;
import com.br.dietamais.dietas.api.AlertaRestricao;
import com.br.dietamais.documentos.api.*;
import com.br.dietamais.metas.api.*;
import com.br.dietamais.pacientes.api.RestricaoSaude;
import com.br.dietamais.prescricoes.api.*;
import com.br.dietamais.rotina.api.*;

/** BE-C9 e BE-C10: exemplos do contrato da 1.4.0 (tipados, válidos e fiéis ao JSON) e formato dos vetores compartilhados com o mobile. */
class ContratoFase9Test {
	final JsonMapper mapper=JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

	record Exemplo(String pasta,String arquivo,Class<?> tipo){}
	static Exemplo f9(String arquivo,Class<?> tipo){return new Exemplo("fase-9",arquivo,tipo);}
	static Stream<Exemplo> exemplos(){
		return Stream.of(
			f9("restricaoSaude.json",RestricaoSaude.class),f9("alertaRestricao.json",AlertaRestricao.class),
			f9("preferenciasAgenda-requisicao.json",PreferenciasAgendaRequisicao.class),f9("preferenciasAgenda-resposta.json",PreferenciasAgendaResposta.class),
			f9("consulta-resposta.json",ConsultaResposta.class),f9("consultaPaciente-resposta.json",ConsultaResposta.class),f9("consulta-requisicao.json",CriarConsultaRequisicao.class),f9("consultaAtualizar-requisicao.json",AtualizarConsultaRequisicao.class),
			f9("registroForaDoPlano-requisicao.json",RegistroForaDoPlanoRequisicao.class),f9("registroForaDoPlano-resposta.json",RegistroForaDoPlanoResposta.class),
			f9("humorAdesao.json",HumorAdesao.class),
			f9("meta-requisicao.json",MetaRequisicao.class),f9("meta-resposta.json",MetaResposta.class),f9("atualizarMeta-requisicao.json",AtualizarMetaRequisicao.class),
			f9("metas-resposta.json",MetasResposta.class),f9("registroMeta-requisicao.json",RegistroMetaRequisicao.class),f9("registroMeta-resposta.json",RegistroMetaResposta.class),
			f9("modeloMeta-requisicao.json",ModeloMetaRequisicao.class),f9("modeloMeta-resposta.json",ModeloMetaResposta.class),f9("modelosMeta-resposta.json",ModelosMetaResposta.class),
			f9("cumprimentoMetas-resposta.json",CumprimentoMetasResposta.class),
			f9("suplemento-requisicao.json",SuplementoRequisicao.class),f9("suplemento-resposta.json",SuplementoResposta.class),
			f9("atualizarSuplemento-requisicao.json",AtualizarSuplementoRequisicao.class),f9("suplementos-resposta.json",SuplementosResposta.class),
			f9("orientacao-requisicao.json",OrientacaoRequisicao.class),f9("orientacao-resposta.json",OrientacaoResposta.class),
			f9("atualizarOrientacao-requisicao.json",AtualizarOrientacaoRequisicao.class),f9("orientacoes-resposta.json",OrientacoesResposta.class),
			f9("modeloOrientacao-requisicao.json",ModeloOrientacaoRequisicao.class),f9("modeloOrientacao-resposta.json",ModeloOrientacaoResposta.class),f9("modelosOrientacao-resposta.json",ModelosOrientacaoResposta.class),
			f9("modeloDocumento-requisicao.json",ModeloDocumentoRequisicao.class),f9("modeloDocumento-resposta.json",ModeloDocumentoResposta.class),f9("modelosDocumento-resposta.json",ModelosDocumentoResposta.class),
			f9("emitirDocumento-requisicao.json",EmitirDocumentoRequisicao.class),f9("documento-resposta.json",DocumentoResposta.class),f9("documentos-resposta.json",DocumentosResposta.class),
			f9("anularDocumento-requisicao.json",AnularDocumentoRequisicao.class),f9("documentoPaciente-resposta.json",DocumentoPacienteResposta.class),f9("documentosPaciente-resposta.json",DocumentosPacienteResposta.class),
			f9("conferenciaDocumento-resposta.json",ConferenciaDocumentoResposta.class),
			f9("erro-alergia-nao-confirmada.json",ProblemaResposta.class),f9("erro-transicao-status-invalida.json",ProblemaResposta.class),f9("erro-link-invalido.json",ProblemaResposta.class),
			f9("erro-meta-invalida.json",ProblemaResposta.class),f9("erro-documento-ja-anulado.json",ProblemaResposta.class),
			new Exemplo("fase-10","plano-resposta.json",PlanoPublicoResposta.class),new Exemplo("fase-10","planos-resposta.json",PlanosPublicosResposta.class),
			new Exemplo("fase-10","checkout-requisicao.json",CheckoutPlanoRequisicao.class),new Exemplo("fase-10","assinaturaAmpliada-resposta.json",AssinaturaAmpliadaResposta.class),
			new Exemplo("fase-10","erro-plano-indisponivel.json",ProblemaResposta.class));
	}

	@TestFactory Stream<DynamicTest> exemplosSaoTipadosValidosEFieisAoJson(){
		return exemplos().map(e->DynamicTest.dynamicTest(e.pasta()+"/"+e.arquivo(),()->{
			var json=recurso("/contrato/"+e.pasta()+"/"+e.arquivo());Object dto=mapper.readValue(json,e.tipo());
			try(var factory=Validation.buildDefaultValidatorFactory()){assertThat(factory.getValidator().validate(dto)).isEmpty();}
			// requisições aceitam campos omitidos; só respostas precisam voltar idênticas
			if(!e.arquivo().contains("requisicao"))assertThat(mapper.readTree(mapper.writeValueAsString(dto))).isEqualTo(mapper.readTree(json));
			if(dto instanceof ProblemaResposta erro){assertThat(erro.status()).isEqualTo(erro.codigo().status());assertThat(erro.codigo().regras()).contains(erro.regra());}
		}));
	}

	@Test void vetoresDeMetasTemFormatoConhecido() throws Exception {
		var raiz=mapper.readTree(recurso("/contrato/calculos/metas.json"));
		var ids=new HashSet<String>();
		for(JsonNode caso:raiz.path("casos")){
			assertThat(ids.add(caso.path("id").asString())).as("id repetido").isTrue();
			assertThat(caso.path("funcao").asString()).isEqualTo("cumprimento");
			assertThat(caso.path("origem").asString()).isNotBlank();
			var esperado=caso.path("esperado");
			assertThat(esperado.path("diasCumpridos").asInt()).isLessThanOrEqualTo(esperado.path("diasPrevistos").asInt());
			assertThat(esperado.path("melhorSequencia").asInt()).isGreaterThanOrEqualTo(esperado.path("sequenciaAtual").asInt());
		}
		assertThat(ids).hasSizeGreaterThanOrEqualTo(10);
	}

	@Test void vetoresDeAlertasTemFormatoConhecido() throws Exception {
		var raiz=mapper.readTree(recurso("/contrato/calculos/alertas-restricao.json"));
		var ids=new HashSet<String>();
		for(JsonNode caso:raiz.path("casos")){
			assertThat(ids.add(caso.path("id").asString())).as("id repetido").isTrue();
			assertThat(caso.path("funcao").asString()).isEqualTo("alertasItem");
			assertThat(caso.path("origem").asString()).isNotBlank();
			for(JsonNode alerta:caso.path("esperado").path("alertas"))
				assertThat(alerta.path("exigeConfirmacao").asBoolean()).isEqualTo("ALERGIA".equals(alerta.path("tipo").asString()));
		}
		assertThat(ids).hasSizeGreaterThanOrEqualTo(6);
	}

	String recurso(String caminho) throws Exception {try(var input=getClass().getResourceAsStream(caminho)){assertThat(input).as(caminho).isNotNull();return new String(input.readAllBytes(),StandardCharsets.UTF_8);}}
}
