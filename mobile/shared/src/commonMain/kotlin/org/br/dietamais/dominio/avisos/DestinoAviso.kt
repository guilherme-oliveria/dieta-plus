package org.br.dietamais.dominio.avisos

import org.br.dietamais.app.PerfilNavegacao
import kotlin.uuid.Uuid

sealed interface DestinoAviso {
    data object Hoje : DestinoAviso
    data object Dieta : DestinoAviso
    /** MOB-93: aba Dieta com as Orientações abertas (push "nova orientação"). */
    data object Orientacoes : DestinoAviso
    data object Agenda : DestinoAviso
    /** Conta da nutri, onde fica a assinatura (aviso de fim do teste, BE-82). */
    data object Conta : DestinoAviso
    /** [fichaId] é o id da ficha na rota `/pacientes/{id}`. */
    data class Paciente(val fichaId: String) : DestinoAviso
    data object Nenhum : DestinoAviso
}

/**
 * Traduz a `rota` do aviso (contrato-fase-4.md, BE-24) para uma tela do perfil. Rota desconhecida ou de outro perfil
 * não faz nada: o servidor pode evoluir antes do app.
 */
fun destinoDaRota(perfil: PerfilNavegacao, rota: String?): DestinoAviso {
    val partes = rota?.trim()?.split('/')?.filter { it.isNotEmpty() }.orEmpty()
    val primeiro = partes.firstOrNull() ?: return DestinoAviso.Nenhum
    return when (perfil) {
        PerfilNavegacao.PACIENTE -> when (primeiro) {
            "dieta" -> DestinoAviso.Dieta
            "orientacoes" -> DestinoAviso.Orientacoes
            "questionarios", "agenda" -> DestinoAviso.Hoje // o questionário e a próxima consulta aparecem no Hoje
            else -> DestinoAviso.Nenhum
        }
        PerfilNavegacao.NUTRICIONISTA -> when {
            primeiro == "agenda" -> DestinoAviso.Agenda
            primeiro == "assinatura" -> DestinoAviso.Conta
            primeiro == "pacientes" && partes.size == 2 && runCatching { Uuid.parse(partes[1]) }.isSuccess -> DestinoAviso.Paciente(partes[1])
            else -> DestinoAviso.Nenhum
        }
    }
}
