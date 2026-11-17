package com.br.dietamais.agenda.api;

/**
 * Status da consulta (RN-AGE-03). {@code ENCERRADA} só existe na projeção do paciente (D-03): REALIZADA e FALTA aparecem para ele assim;
 * nunca é gravada nem aceita na escrita.
 */
public enum StatusConsulta { AGENDADA, REALIZADA, FALTA, CANCELADA, ENCERRADA }
