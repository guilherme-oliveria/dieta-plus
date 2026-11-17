package com.br.dietamais.pacientes.api;

import java.util.List;
import java.util.UUID;

/** Itens de saúde da ficha de um paciente da nutri logada, para a dieta calcular alertas (RN-DIE-14). Vazio quando a ficha não os tem; paciente de outra nutri: 404. */
public interface ConsultaRestricoesParaDieta { List<RestricaoSaude> restricoes(UUID paciente); }
