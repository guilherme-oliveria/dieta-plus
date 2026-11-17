package com.br.dietamais.assinaturas.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PlanosPublicosResposta(@NotNull List<@Valid PlanoPublicoResposta> itens) { }
