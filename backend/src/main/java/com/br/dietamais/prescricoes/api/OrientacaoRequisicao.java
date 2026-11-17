package com.br.dietamais.prescricoes.api;

import jakarta.validation.constraints.*;
import java.util.UUID;

/** `POST /pacientes/{id}/orientacoes` (RN-ORI-01). `texto` em Markdown restrito (negrito, itálico e listas); HTML é descartado, nunca guardado. */
public record OrientacaoRequisicao(UUID id,@NotBlank @Size(max=120) String titulo,@NotBlank @Size(max=5000) String texto) { }
