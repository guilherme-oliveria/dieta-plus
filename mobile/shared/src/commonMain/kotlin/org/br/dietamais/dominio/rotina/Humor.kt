package org.br.dietamais.dominio.rotina

import kotlin.uuid.Uuid

/** BE-73 (RN-ROT-01): escalas de 1 a 5, opcionais. Fome antes de comer e como a pessoa ficou depois. */
const val ESCALA_MIN = 1
const val ESCALA_MAX = 5

fun valorEscala(valor: Int?): Int? = valor?.takeIf { it in ESCALA_MIN..ESCALA_MAX }

/** RN-ROT-06: texto curto do registro fora do plano. */
const val LIMITE_FORA_DO_PLANO = 280

enum class ErroForaDoPlano { TEXTO, HORA }

/** MOB-88: formulário "Comi algo fora do plano". [hora] em HH:MM no fuso do aparelho; [refeicaoId] opcional, só refeição da dieta do dia. */
data class FormularioForaDoPlano(val texto: String = "", val refeicaoId: Uuid? = null, val hora: String = "") {
    override fun toString() = "FormularioForaDoPlano[conteúdo protegido]"
}

/** Valida o formulário (feedback; o backend decide). Devolve os erros e, quando válido, hora e minuto. */
fun validarForaDoPlano(f: FormularioForaDoPlano): Pair<Set<ErroForaDoPlano>, Pair<Int, Int>?> {
    val erros = mutableSetOf<ErroForaDoPlano>()
    val texto = f.texto.trim()
    if (texto.isEmpty() || texto.length > LIMITE_FORA_DO_PLANO) erros += ErroForaDoPlano.TEXTO
    val hora = lerHoraMinuto(f.hora)
    if (hora == null) erros += ErroForaDoPlano.HORA
    return erros to hora.takeIf { erros.isEmpty() }
}

/** Aceita HH:MM e H:MM. */
fun lerHoraMinuto(texto: String): Pair<Int, Int>? {
    val partes = texto.trim().split(':')
    if (partes.size != 2 || partes[1].trim().length != 2) return null
    val h = partes[0].trim().toIntOrNull() ?: return null
    val m = partes[1].trim().toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) h to m else null
}
