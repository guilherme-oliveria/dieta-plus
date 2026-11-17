package com.br.dietamais.prescricoes;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.prescricoes.dominio.MarkdownRestrito;
import org.junit.jupiter.api.Test;

/** RN-ORI-01: Markdown restrito; HTML nunca é guardado. */
class MarkdownRestritoTest {
	@Test void mantemNegritoItalicoEListas() {
		String t="**Beba água**\n\n- *devagar*\n- sempre\n\n1. manhã\n2. noite";
		assertThat(MarkdownRestrito.limpar(t)).isEqualTo(t);
	}
	@Test void descartaHtmlScriptEComentarios() {
		assertThat(MarkdownRestrito.limpar("Oi <b>você</b><script>alert(1)</script> <!-- x -->tudo bem<img src=x onerror=alert(1)>")).isEqualTo("Oi você tudo bem");
		assertThat(MarkdownRestrito.limpar("<<script>script>alert(1)<</script>/script>")).doesNotContain("<").doesNotContain(">");
	}
	@Test void linksEImagensViramSoTexto() {
		assertThat(MarkdownRestrito.limpar("Veja [aqui](javascript:alert(1)) e ![foto](http://x/y.png)")).isEqualTo("Veja aqui e foto");
	}
	@Test void normalizaQuebrasEAparaEspacos() {
		assertThat(MarkdownRestrito.limpar("  linha 1\r\nlinha 2  ")).isEqualTo("linha 1\nlinha 2");
		assertThat(MarkdownRestrito.limpar(null)).isEmpty();
	}
}
