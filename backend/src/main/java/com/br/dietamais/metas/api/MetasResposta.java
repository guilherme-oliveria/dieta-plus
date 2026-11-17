package com.br.dietamais.metas.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record MetasResposta(@NotNull List<@Valid MetaResposta> itens) { }
