package com.br.dietamais.metas.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ModeloMetaRequisicao(@NotBlank @Size(max=80) String nome,@NotEmpty @Size(max=20) List<@Valid ItemModeloMeta> itens) { }
