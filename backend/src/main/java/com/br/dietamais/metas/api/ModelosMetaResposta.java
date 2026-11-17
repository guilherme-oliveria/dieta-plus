package com.br.dietamais.metas.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ModelosMetaResposta(@NotNull List<@Valid ModeloMetaResposta> itens) { }
