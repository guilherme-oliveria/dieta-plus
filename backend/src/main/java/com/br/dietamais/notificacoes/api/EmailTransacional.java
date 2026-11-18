package com.br.dietamais.notificacoes.api;

import java.time.Instant;

/**
 * E-mail a enviar. {@code inicio} e {@code link} só existem na confirmação de consulta (BE-72): o horário e, na consulta online,
 * o link da sala. Nunca levam dado de saúde (RN-NOT-02).
 */
public record EmailTransacional(TipoEmail tipo, String destinatario, String token, Instant expiraEm, Instant inicio, String link) {
	public EmailTransacional(TipoEmail tipo, String destinatario, String token, Instant expiraEm) { this(tipo, destinatario, token, expiraEm, null, null); }
	@Override public String toString() { return "EmailTransacional[dados protegidos]"; }
}
