package org.br.dietamais.dominio.saude

import org.br.dietamais.data.remoto.dto.*
import kotlin.test.*
import kotlin.uuid.Uuid

/** MOB-82 (RN-SAU-01 e 02): selo, sugestão de marcadores, montagem e validação dos itens de saúde. */
class RestricoesSaudeTest {
    private fun r(tipo: TipoRestricaoSaude, nome: String, g: GravidadeRestricao? = null, m: List<String> = emptyList()) = RestricaoSaudeDto(Uuid.random(), tipo, nome, g, marcadores = m)

    @Test fun seloPegaAAlergiaMaisGraveEContaAsOutrasAlergiasEIntolerancias() {
        val lista = listOf(r(TipoRestricaoSaude.CONDICAO, "Hipertensão"), r(TipoRestricaoSaude.INTOLERANCIA, "Lactose", GravidadeRestricao.GRAVE),
            r(TipoRestricaoSaude.ALERGIA, "Camarão", GravidadeRestricao.LEVE), r(TipoRestricaoSaude.ALERGIA, "Amendoim", GravidadeRestricao.GRAVE),
            r(TipoRestricaoSaude.MEDICAMENTO, "Losartana"))
        val selo = assertNotNull(RestricoesSaude.selo(lista))
        assertEquals("Amendoim", selo.principal.nome); assertEquals(2, selo.outras)
        assertNull(RestricoesSaude.selo(listOf(r(TipoRestricaoSaude.CONDICAO, "Diabetes"), r(TipoRestricaoSaude.MEDICAMENTO, "Metformina"))))
        assertNull(RestricoesSaude.selo(emptyList()))
        // sem gravidade, vale a ordem de cadastro
        assertEquals("Ovo", RestricoesSaude.selo(listOf(r(TipoRestricaoSaude.ALERGIA, "Ovo"), r(TipoRestricaoSaude.ALERGIA, "Soja")))!!.principal.nome)
    }

    @Test fun sugereMarcadoresPeloNomeSemAcentoESemFalsoPositivo() {
        assertEquals(setOf("AMENDOIM"), RestricoesSaude.sugerirMarcadores("Amendoim"))
        assertEquals(setOf("CONTEM_LACTOSE"), RestricoesSaude.sugerirMarcadores("Intolerância à LACTOSE"))
        assertEquals(setOf("CONTEM_LACTOSE"), RestricoesSaude.sugerirMarcadores("Proteína do leite"))
        assertEquals(setOf("CONTEM_GLUTEN"), RestricoesSaude.sugerirMarcadores("Doença celíaca"))
        assertEquals(setOf("FRUTOS_DO_MAR"), RestricoesSaude.sugerirMarcadores("Camarão"))
        assertEquals(setOf("OVO"), RestricoesSaude.sugerirMarcadores("Ovos"))
        assertEquals(emptySet(), RestricoesSaude.sugerirMarcadores("Novo remédio"))
        assertEquals(emptySet(), RestricoesSaude.sugerirMarcadores("Polvo"))
        assertEquals(emptySet(), RestricoesSaude.sugerirMarcadores(" "))
    }

    @Test fun itemLimpaTextosEMarcadoresSoEmAlergiaEIntolerancia() {
        val id = Uuid.random()
        val alergia = RestricoesSaude.item(FormularioItemSaude(tipo = TipoRestricaoSaude.ALERGIA, nome = "  Amendoim ", observacao = "  ", marcadores = setOf("SOJA", "AMENDOIM", "NOVO_X")), { id })
        assertEquals(id, alergia.id); assertEquals("Amendoim", alergia.nome); assertNull(alergia.observacao)
        assertEquals(listOf("AMENDOIM", "SOJA", "NOVO_X"), alergia.marcadores)
        val condicao = RestricoesSaude.item(FormularioItemSaude(tipo = TipoRestricaoSaude.CONDICAO, nome = "Hipertensão", marcadores = setOf("ALTO_SODIO")), { id })
        assertTrue(condicao.marcadores.isEmpty())
        val existente = Uuid.random()
        assertEquals(existente, RestricoesSaude.item(FormularioItemSaude(id = existente, nome = "X"), { id }).id)
    }

    @Test fun validaNomeObservacaoELimite() {
        assertEquals(ErroItemSaude.NOME_OBRIGATORIO, RestricoesSaude.validar(FormularioItemSaude(nome = " "), 0))
        assertEquals(ErroItemSaude.NOME_LONGO, RestricoesSaude.validar(FormularioItemSaude(nome = "a".repeat(121)), 0))
        assertEquals(ErroItemSaude.OBSERVACAO_LONGA, RestricoesSaude.validar(FormularioItemSaude(nome = "a", observacao = "b".repeat(501)), 0))
        assertEquals(ErroItemSaude.LIMITE_ITENS, RestricoesSaude.validar(FormularioItemSaude(nome = "a"), 50))
        assertNull(RestricoesSaude.validar(FormularioItemSaude(id = Uuid.random(), nome = "a"), 50)) // editar não conta como novo
        assertNull(RestricoesSaude.validar(FormularioItemSaude(nome = "a"), 49))
    }

    @Test fun listaSubstituiOuAcrescentaERemoveEOrdenaPorTipo() {
        val a = r(TipoRestricaoSaude.MEDICAMENTO, "Losartana"); val b = r(TipoRestricaoSaude.ALERGIA, "Ovo"); val c = r(TipoRestricaoSaude.CONDICAO, "Diabetes")
        val lista = RestricoesSaude.salvarNaLista(listOf(a, b), c)
        assertEquals(listOf(a, b, c), lista)
        val editado = b.copy(nome = "Ovo de galinha")
        assertEquals(listOf(a, editado, c), RestricoesSaude.salvarNaLista(lista, editado))
        assertEquals(listOf(a, c), RestricoesSaude.remover(lista, b.id))
        assertEquals(listOf(b, c, a), RestricoesSaude.ordenar(lista))
    }
}
