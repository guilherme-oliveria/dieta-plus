package com.br.dietamais.metas.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Cumprimento de uma meta na janela (RN-MET-03; vetores em `contrato/calculos/metas.json`). `percentual` de 0 a 100. */
public record CumprimentoMeta(
		@NotNull UUID metaId,
		@NotNull TipoMeta tipo,
		@Schema(nullable=true) String nome,
		@NotNull @PositiveOrZero Integer diasPrevistos,
		@NotNull @PositiveOrZero Integer diasCumpridos,
		@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal percentual,
		@NotNull @PositiveOrZero Integer sequenciaAtual,
		@NotNull @PositiveOrZero Integer melhorSequencia) {
}
