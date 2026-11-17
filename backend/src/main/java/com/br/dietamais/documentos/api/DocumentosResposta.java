package com.br.dietamais.documentos.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DocumentosResposta(@NotNull List<@Valid DocumentoResposta> itens) { }
