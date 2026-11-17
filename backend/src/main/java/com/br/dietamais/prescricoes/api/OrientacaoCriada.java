package com.br.dietamais.prescricoes.api;

import java.util.UUID;

/** Evento publicado depois que a nutri cria uma orientação; dispara o aviso genérico ao paciente (RN-NOT-01 e 02). */
public record OrientacaoCriada(UUID orientacaoId,UUID pacienteId) { }
