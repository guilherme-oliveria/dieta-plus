package com.br.dietamais.assinaturas.api;

import jakarta.validation.constraints.*;

/** `POST /assinatura/checkout` ampliado (BE-C10): `periodicidade` ausente = MENSAL, como o app 1.3 envia só `plano` (RN-ASS-06 e 09). */
public record CheckoutPlanoRequisicao(@NotBlank @Size(max=40) String planoCodigo,Periodicidade periodicidade) { }
