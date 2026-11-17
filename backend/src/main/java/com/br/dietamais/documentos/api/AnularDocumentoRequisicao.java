package com.br.dietamais.documentos.api;

import jakarta.validation.constraints.*;

/** `POST /documentos/{id}/anular`: o motivo é obrigatório e fica na auditoria (RN-DOC-02). */
public record AnularDocumentoRequisicao(@NotBlank @Size(max=300) String motivo) { }
