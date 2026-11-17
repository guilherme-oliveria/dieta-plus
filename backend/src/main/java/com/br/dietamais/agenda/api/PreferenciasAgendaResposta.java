package com.br.dietamais.agenda.api;

import jakarta.validation.constraints.NotNull;

public record PreferenciasAgendaResposta(@NotNull AntecedenciaLembrete antecedencia,@NotNull Boolean lembreteExtraUmaHora) { }
