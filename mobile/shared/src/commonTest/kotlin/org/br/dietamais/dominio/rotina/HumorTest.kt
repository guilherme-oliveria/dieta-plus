package org.br.dietamais.dominio.rotina

import kotlin.test.*

class HumorTest {
    @Test fun escalaAceitaSoDeUmACinco() {
        assertEquals(1, valorEscala(1)); assertEquals(5, valorEscala(5))
        assertNull(valorEscala(0)); assertNull(valorEscala(6)); assertNull(valorEscala(null))
    }

    @Test fun lerHoraAceitaHHMMEHMM() {
        assertEquals(8 to 5, lerHoraMinuto("08:05")); assertEquals(8 to 5, lerHoraMinuto(" 8:05 ")); assertEquals(23 to 59, lerHoraMinuto("23:59"))
        listOf("", "8", "8:5", "24:00", "12:60", "ab:cd", "12:00:00", "-1:10").forEach { assertNull(lerHoraMinuto(it), it) }
    }

    @Test fun validarForaDoPlanoExigeTextoCurtoEHoraValida() {
        assertEquals(emptySet<ErroForaDoPlano>() to (12 to 30), validarForaDoPlano(FormularioForaDoPlano("  Pastel  ", null, "12:30")))
        assertEquals(setOf(ErroForaDoPlano.TEXTO, ErroForaDoPlano.HORA) to null, validarForaDoPlano(FormularioForaDoPlano("   ", null, "")))
        assertEquals(setOf(ErroForaDoPlano.TEXTO), validarForaDoPlano(FormularioForaDoPlano("x".repeat(281), null, "12:30")).first)
        assertEquals(emptySet(), validarForaDoPlano(FormularioForaDoPlano("x".repeat(280), null, "12:30")).first)
    }

    @Test fun formularioNaoExpoeOTextoNoToString() {
        assertFalse(FormularioForaDoPlano("Brigadeiro", null, "10:00").toString().contains("Brigadeiro"))
    }
}
