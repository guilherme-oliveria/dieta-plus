package com.br.dietamais.prescricoes.api;

import jakarta.validation.constraints.*;

public record ModeloOrientacaoRequisicao(@NotBlank @Size(max=120) String titulo,@NotBlank @Size(max=5000) String texto) { }
