package com.br.dietamais.documentos.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DocumentosPacienteResposta(@NotNull List<@Valid DocumentoPacienteResposta> itens) { }
