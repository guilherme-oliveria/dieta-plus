package org.br.dietamais.dominio.prescricoes

import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.dto.*
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PrescricoesTest {
    private val hoje = LocalDate(2026, 11, 17)
    private val t = Instant.parse("2026-11-01T10:00:00Z")
    private fun sup(inicio: LocalDate, fim: LocalDate? = null, produto: Uuid? = null) =
        SuplementoResposta(Uuid.random(), Uuid.random(), produto, "Vitamina D3", "1 cápsula", "Cápsula", "Todo dia", listOf("08:00"), inicio, fim, 1, t, t)

    @Test fun horariosNormalizaOrdenaELimita() {
        assertEquals(listOf("08:00", "20:00"), lerHorarios(" 20:00, 8:00 ;08:00"))
        assertEquals(emptyList(), lerHorarios("  "))
        assertNull(lerHorarios("8h")); assertNull(lerHorarios("25:00"))
        assertNull(lerHorarios("01:00,02:00,03:00,04:00,05:00,06:00,07:00"))
    }

    @Test fun validaProdutoOuNomeDoseHorariosEDatas() {
        val base = novoFormularioSuplemento(hoje, "Todo dia").copy(dose = "3 g")
        assertEquals(setOf(ErroSuplemento.PRODUTO), validarSuplemento(base))
        assertEquals(emptySet(), validarSuplemento(base.copy(produtoId = Uuid.random(), produtoNome = "Creatina")))
        assertEquals(setOf(ErroSuplemento.NOME), validarSuplemento(base.copy(origem = OrigemSuplemento.TEXTO_LIVRE)))
        val livre = base.copy(origem = OrigemSuplemento.TEXTO_LIVRE, nome = "Ômega 3")
        assertEquals(setOf(ErroSuplemento.DOSE), validarSuplemento(livre.copy(dose = " ")))
        assertEquals(setOf(ErroSuplemento.HORARIOS), validarSuplemento(livre.copy(horarios = "8h")))
        assertEquals(setOf(ErroSuplemento.FIM), validarSuplemento(livre.copy(fim = "16/11/2026")))
        assertEquals(setOf(ErroSuplemento.INICIO), validarSuplemento(livre.copy(inicio = "")))
    }

    @Test fun requisicaoMandaProdutoOuNomeNuncaOsDois() {
        val id = Uuid.random(); val produto = Uuid.random()
        val f = novoFormularioSuplemento(hoje, "Todo dia").copy(produtoId = produto, produtoNome = "Creatina", nome = "sobra", dose = " 3 g ", forma = "Pó", horarios = "16:00")
        assertEquals(SuplementoRequisicao(id, produto, null, "3 g", "Pó", "Todo dia", listOf("16:00"), hoje, null), requisicaoSuplemento(f, id))
        val livre = requisicaoSuplemento(f.copy(origem = OrigemSuplemento.TEXTO_LIVRE, nome = " Ômega 3 "), id)
        assertNull(livre.produtoId); assertEquals("Ômega 3", livre.nome)
    }

    @Test fun edicaoNaoMexeEmNomeNemProduto() {
        val s = sup(LocalDate(2026, 11, 3), produto = Uuid.random())
        val f = formularioDe(s).copy(dose = "2 cápsulas", fim = "")
        assertTrue(f.edicao); assertEquals(OrigemSuplemento.PRODUTO, f.origem); assertEquals("03/11/2026", f.inicio)
        assertEquals(emptySet(), validarSuplemento(f))
        assertEquals(AtualizarSuplementoRequisicao("2 cápsulas", "Cápsula", "Todo dia", listOf("08:00"), null), atualizacaoSuplemento(f))
    }

    @Test fun emUsoEEncerrados() {
        val a = sup(LocalDate(2026, 11, 1)); val b = sup(LocalDate(2026, 11, 10), fim = hoje); val c = sup(LocalDate(2026, 10, 1), fim = LocalDate(2026, 11, 1))
        assertEquals(listOf(b, a), suplementosEmUso(listOf(a, b, c), hoje)); assertEquals(listOf(c), suplementosEncerrados(listOf(a, b, c), hoje))
    }

    @Test fun orientacaoValidaEBotoesDeFormato() {
        assertEquals(setOf(ErroOrientacao.TITULO, ErroOrientacao.TEXTO), validarOrientacao(FormularioOrientacao()))
        assertEquals(setOf(ErroOrientacao.TEXTO), validarOrientacao(FormularioOrientacao("Prato", "x".repeat(5001))))
        assertEquals("Use **negrito**", acrescentarNegrito("Use"))
        assertEquals("**negrito**", acrescentarNegrito(""))
        assertEquals("Itens:\n- ", acrescentarItemLista("Itens:")); assertEquals("- ", acrescentarItemLista(""))
    }

    @Test fun orientacoesVisiveisPrimeiro() {
        val velha = OrientacaoResposta(Uuid.random(), Uuid.random(), "A", "x", null, 1, t, t)
        val nova = velha.copy(id = Uuid.random(), criadoEm = Instant.parse("2026-11-10T00:00:00Z"))
        val arquivada = velha.copy(id = Uuid.random(), arquivadaEm = t, criadoEm = Instant.parse("2026-11-12T00:00:00Z"))
        assertEquals(listOf(nova, velha, arquivada), orientacoesOrdenadas(listOf(arquivada, velha, nova)))
    }
}
