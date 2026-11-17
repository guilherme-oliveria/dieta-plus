package com.br.dietamais.dietas.api;

import jakarta.validation.constraints.*;
import java.util.UUID;

/**
 * Alerta calculado pelo servidor para um item da dieta (`alertas[]` do item, BE-C9, H-01, RN-DIE-14).
 * `tipo` é `ALERGIA` ou `INTOLERANCIA` (texto, para o módulo não depender de `pacientes`). Alergia exige `alergiaConfirmada` no item para publicar.
 * O marcador é sugestão: a conduta é da nutri.
 */
public record AlertaRestricao(
		@NotNull UUID restricaoId,
		@NotBlank String tipo,
		@NotBlank String nome,
		@NotBlank String marcador,
		@NotNull Boolean exigeConfirmacao) {
}
