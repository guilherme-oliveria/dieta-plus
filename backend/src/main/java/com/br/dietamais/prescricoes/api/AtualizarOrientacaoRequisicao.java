package com.br.dietamais.prescricoes.api;

import jakarta.validation.constraints.Size;

/** `PATCH /orientacoes/{id}` com `If-Match`; `arquivar=true` a tira do app do paciente (a entidade não é apagada). */
public record AtualizarOrientacaoRequisicao(@Size(max=120) String titulo,@Size(max=5000) String texto,Boolean arquivar) { }
