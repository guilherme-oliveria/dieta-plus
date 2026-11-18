package com.br.dietamais.documentos.dominio;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** RN-DOC-01 e 03: preenchimento dos campos e código de conferência (sem banco). */
class PreenchimentoDocumentoTest {
	@Test void preencheOsCincoCamposEAceitaEspacosNoNome(){
		var r=PreenchimentoDocumento.preencher("{{paciente}} em {{ data }} às {{horario}}. {{nutri}} ({{crn}})",
			Map.of("paciente","Ana Lima","data","17/11/2026","horario","15:00","nutri","Marina Costa","crn","CRN-3 00000"));
		assertThat(r.completo()).isTrue();
		assertThat(r.texto()).isEqualTo("Ana Lima em 17/11/2026 às 15:00. Marina Costa (CRN-3 00000)");
	}
	@Test void campoSemValorOuDesconhecidoNaoEmite(){
		var valores=new HashMap<String,String>();valores.put("paciente","Ana");valores.put("horario",null);
		var r=PreenchimentoDocumento.preencher("{{paciente}} às {{horario}} {{cpf}}",valores);
		assertThat(r.completo()).isFalse();assertThat(r.faltando()).containsExactly("horario","cpf");
	}
	@Test void valorComCifraoOuBarraNaoQuebraATroca(){
		var r=PreenchimentoDocumento.preencher("Paciente: {{paciente}}",Map.of("paciente","Jo$é \\1"));
		assertThat(r.texto()).isEqualTo("Paciente: Jo$é \\1");
	}
	@Test void limpaControlesEQuebras(){
		assertThat(PreenchimentoDocumento.limpar("  a\r\nb\u0000c\rd  ")).isEqualTo("a\nbc\nd");
		assertThat(PreenchimentoDocumento.limpar(null)).isEmpty();
	}
	@Test void codigoTemFormatoAlfabetoSemAmbiguosENormaliza(){
		var gerador=new CodigoConferencia(new Random(42));var vistos=new HashSet<String>();
		for(int i=0;i<500;i++){
			String c=gerador.novo();
			assertThat(c).matches("^[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}$").doesNotContain("0","O","1","I","L");
			vistos.add(c);
			assertThat(CodigoConferencia.normalizar(c.toLowerCase().replace("-"," "))).isEqualTo(c);
		}
		assertThat(vistos).hasSize(500);
		assertThat(CodigoConferencia.normalizar("K7M2-Q9XP-4HTR")).isEqualTo("K7M2-Q9XP-4HTR");
		assertThat(CodigoConferencia.normalizar("K7M2-Q9XP-4HT")).isNull();
		assertThat(CodigoConferencia.normalizar("O7M2-Q9XP-4HTR")).isNull();
		assertThat(CodigoConferencia.normalizar(null)).isNull();
	}
}
