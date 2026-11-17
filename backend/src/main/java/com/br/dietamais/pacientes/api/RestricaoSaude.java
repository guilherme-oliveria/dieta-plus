package com.br.dietamais.pacientes.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/**
 * Item de saúde da ficha (BE-C9, H-01). Mesmo formato na requisição (`restricoesSaude[]` de `POST/PATCH /pacientes` e da coleção `pacientes` do sync)
 * e na resposta; a lista inteira substitui a anterior. Dado clínico da nutri: nunca sai na projeção do paciente (RN-SAU-01).
 * `marcadores` só em ALERGIA e INTOLERANCIA (códigos de `MarcadorAlimento`, RN-SAU-02); em CONDICAO e MEDICAMENTO é recusado com `ITEM_INVALIDO`.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RestricaoSaude(
		@Schema(nullable=true,description="Gerado no app (UUIDv7) ou no servidor") UUID id,
		@NotNull TipoRestricaoSaude tipo,
		@NotBlank @Size(max=120) String nome,
		@Schema(nullable=true) GravidadeRestricao gravidade,
		@Schema(nullable=true) @Size(max=500) String observacao,
		@Schema(nullable=true) @Size(max=20) List<@NotBlank @Size(max=40) String> marcadores) {
}
