package com.br.dietamais.agenda.api;

import jakarta.validation.constraints.NotNull;

/** `PUT /eu/nutri/preferencias-agenda` (RN-AGE-05, BE-72). Padrão: UM_DIA e sem lembrete extra. */
public record PreferenciasAgendaRequisicao(@NotNull AntecedenciaLembrete antecedencia,@NotNull Boolean lembreteExtraUmaHora) { }
