package com.br.dietamais.prescricoes.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ModelosOrientacaoResposta(@NotNull List<@Valid ModeloOrientacaoResposta> itens) { }
