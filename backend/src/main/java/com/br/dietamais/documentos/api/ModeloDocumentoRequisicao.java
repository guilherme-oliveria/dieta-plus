package com.br.dietamais.documentos.api;

import jakarta.validation.constraints.*;

/**
 * `POST/PUT /eu/nutri/modelos-documento` (RN-DOC-01). Campos de preenchimento no texto: `{{paciente}}`, `{{data}}`, `{{horario}}`, `{{nutri}}`, `{{crn}}`.
 * Modelo do sistema é somente leitura (`403` ao editar ou apagar); a nutri o copia criando um novo com o mesmo texto.
 */
public record ModeloDocumentoRequisicao(@NotNull TipoDocumento tipo,@NotBlank @Size(max=80) String nome,@NotBlank @Size(max=5000) String texto) { }
