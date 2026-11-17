package com.br.dietamais.metas.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** `GET /pacientes/{id}/metas/cumprimento?de=&ate=` (padrão: últimos 7 dias, até hoje). */
public record CumprimentoMetasResposta(@NotNull LocalDate de,@NotNull LocalDate ate,@NotNull List<@Valid CumprimentoMeta> metas) { }
