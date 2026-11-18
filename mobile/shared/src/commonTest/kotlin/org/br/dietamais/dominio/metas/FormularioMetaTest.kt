package org.br.dietamais.dominio.metas

import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.dto.*
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FormularioMetaTest {
    private val hoje = LocalDate(2026, 11, 17)
    private fun meta(tipo: TipoMeta, fim: LocalDate? = null, criada: String = "2026-11-01T10:00:00Z", nome: String? = null) =
        MetaResposta(Uuid.random(), Uuid.random(), tipo, nome, if (tipo == TipoMeta.SIM_NAO) 1.0 else 30.0, null, FrequenciaMeta.DIARIA, fim = fim,
            versao = 1, criadoEm = Instant.parse(criada), atualizadoEm = Instant.parse(criada))

    @Test fun novoFormularioComecaHojeSemAgua() {
        assertEquals("17/11/2026", novoFormularioMeta(hoje).inicio)
        assertFalse(TipoMeta.AGUA in TIPOS_CRIAVEIS)
    }

    @Test fun validaNomeAlvoVezesEDatas() {
        val base = FormularioMeta(TipoMeta.ATIVIDADE_MIN, alvo = "30", inicio = "17/11/2026")
        assertEquals(emptySet(), validarMeta(base))
        assertEquals(setOf(ErroMeta.ALVO), validarMeta(base.copy(alvo = "0")))
        assertEquals(setOf(ErroMeta.ALVO), validarMeta(base.copy(alvo = "abc")))
        assertEquals(setOf(ErroMeta.NOME), validarMeta(base.copy(tipo = TipoMeta.CONTADOR)))
        assertEquals(emptySet(), validarMeta(base.copy(tipo = TipoMeta.SIM_NAO, nome = "Sem refrigerante", alvo = "")))
        assertEquals(setOf(ErroMeta.VEZES), validarMeta(base.copy(frequencia = FrequenciaMeta.SEMANAL, vezesPorSemana = 8)))
        assertEquals(setOf(ErroMeta.INICIO), validarMeta(base.copy(inicio = "31/02/2026")))
        assertEquals(setOf(ErroMeta.FIM), validarMeta(base.copy(fim = "16/11/2026")))
        assertEquals(setOf(ErroMeta.FIM), validarMeta(base.copy(fim = "xx")))
    }

    @Test fun requisicaoLimpaCamposQueOTipoNaoAceita() {
        val id = Uuid.random()
        val r = requisicaoMeta(FormularioMeta(TipoMeta.PASSOS, nome = "ignorado", alvo = "7000", unidade = "x", frequencia = FrequenciaMeta.DIARIA, vezesPorSemana = 4, inicio = "17/11/2026"), id)
        assertEquals(MetaRequisicao(id, TipoMeta.PASSOS, null, 7000.0, null, FrequenciaMeta.DIARIA, null, hoje, null), r)
        val s = requisicaoMeta(FormularioMeta(TipoMeta.SIM_NAO, nome = " Sem refri ", alvo = "", frequencia = FrequenciaMeta.SEMANAL, vezesPorSemana = 5), id)
        assertEquals(1.0, s.alvo); assertEquals("Sem refri", s.nome); assertEquals(5, s.vezesPorSemana); assertNull(s.inicio)
        val c = requisicaoMeta(FormularioMeta(TipoMeta.CONTADOR, nome = "Copos de chá", alvo = "2,5", unidade = "copos"), id)
        assertEquals(2.5, c.alvo); assertEquals("copos", c.unidade)
    }

    @Test fun edicaoDaAguaSoMandaOAlvo() {
        val agua = meta(TipoMeta.AGUA).copy(alvo = 2100.0, alvoPadrao = true)
        val f = formularioDe(agua).copy(alvo = "2500")
        assertEquals("2100", formularioDe(agua).alvo); assertTrue(f.edicao)
        assertEquals(AtualizarMetaRequisicao(alvo = 2500.0), atualizacaoMeta(f))
    }

    @Test fun modeloNaoLevaAAgua() {
        val itens = itensDeModelo(listOf(meta(TipoMeta.AGUA), meta(TipoMeta.SIM_NAO, nome = "Sem refri"), meta(TipoMeta.OUTRO)))
        assertEquals(listOf(TipoMeta.SIM_NAO), itens.map { it.tipo }); assertEquals("Sem refri", itens.single().nome)
    }

    @Test fun ativasTiramEncerradasEPoemAguaPrimeiro() {
        val passos = meta(TipoMeta.PASSOS, criada = "2026-10-01T00:00:00Z")
        val agua = meta(TipoMeta.AGUA, criada = "2026-11-01T00:00:00Z")
        val antiga = meta(TipoMeta.SONO_HORAS, fim = LocalDate(2026, 11, 16))
        val hojeFim = meta(TipoMeta.FRUTAS_VERDURAS, fim = hoje, criada = "2026-11-02T00:00:00Z")
        assertEquals(listOf(agua, passos, hojeFim), metasAtivas(listOf(passos, antiga, hojeFim, agua), hoje))
    }

    @Test fun textoDoAlvoEAbaixoDaMetade() {
        assertEquals("2", textoAlvo(2.0)); assertEquals("7,5", textoAlvo(7.5))
        assertTrue(abaixoDaMetade(43.0)); assertFalse(abaixoDaMetade(50.0)); assertFalse(abaixoDaMetade(null))
    }
}
