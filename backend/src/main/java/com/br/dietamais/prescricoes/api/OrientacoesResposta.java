package com.br.dietamais.prescricoes.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OrientacoesResposta(@NotNull List<@Valid OrientacaoResposta> itens) { }
