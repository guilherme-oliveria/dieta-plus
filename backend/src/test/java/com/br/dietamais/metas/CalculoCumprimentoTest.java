package com.br.dietamais.metas;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.metas.api.FrequenciaMeta;
import com.br.dietamais.metas.dominio.CalculoCumprimento;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** BE-74, RN-MET-03: a função do servidor tem de bater com os vetores compartilhados com o app. */
class CalculoCumprimentoTest {
	static Stream<JsonNode> casos() throws Exception {
		try(var in=CalculoCumprimentoTest.class.getResourceAsStream("/contrato/calculos/metas.json")){
			var raiz=JsonMapper.builder().build().readTree(new String(in.readAllBytes(),StandardCharsets.UTF_8));
			var l=new ArrayList<JsonNode>();raiz.path("casos").forEach(l::add);return l.stream();
		}
	}
	static LocalDate data(JsonNode n){return n.isMissingNode()||n.isNull()?null:LocalDate.parse(n.asString());}

	@ParameterizedTest(name="{0}") @MethodSource("casos") void bateComOsVetores(JsonNode caso){
		var e=caso.path("entrada");var meta=e.path("meta");var soma=new HashMap<LocalDate,BigDecimal>();
		for(var r:e.path("registros"))soma.merge(LocalDate.parse(r.path("data").asString()),r.path("valor").decimalValue(),BigDecimal::add);
		var vezes=meta.path("vezesPorSemana");
		var r=CalculoCumprimento.calcular(FrequenciaMeta.valueOf(meta.path("frequencia").asString()),meta.path("alvo").decimalValue(),vezes.isMissingNode()?null:vezes.asInt(),
			data(meta.path("inicio")),data(meta.path("fim")),data(e.path("janela").path("de")),data(e.path("janela").path("ate")),data(e.path("hoje")),soma);
		var x=caso.path("esperado");
		assertThat(r.diasPrevistos()).as("previstos").isEqualTo(x.path("diasPrevistos").asInt());
		assertThat(r.diasCumpridos()).as("cumpridos").isEqualTo(x.path("diasCumpridos").asInt());
		assertThat(r.percentual()).as("percentual").isEqualByComparingTo(x.path("percentual").decimalValue());
		assertThat(r.sequenciaAtual()).as("sequência atual").isEqualTo(x.path("sequenciaAtual").asInt());
		assertThat(r.melhorSequencia()).as("melhor sequência").isEqualTo(x.path("melhorSequencia").asInt());
	}
}
