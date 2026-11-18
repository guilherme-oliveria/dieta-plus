package org.br.dietamais.dominio.assinatura

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.*
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PlanosTest {
    private val basico = PlanoResposta("BASICO", "Básico", 29.9, 20, 299.0)
    private val consultorio = PlanoResposta("CONSULTORIO", "Consultório", 79.9, 80, 799.0)
    private val plus = PlanoResposta("PLUS", "Plus", 179.9, null, 1799.0)
    private val soMensal = PlanoResposta("AVULSO", "Avulso", 19.9, 10)
    private fun assinatura(status: StatusAssinatura = StatusAssinatura.TESTE, plano: PlanoResposta = PlanoResposta("TESTE", "Teste", 0.0, 5),
        periodicidade: Periodicidade? = null, exibe: Boolean? = null, pagamento: Boolean = true, troca: TrocaPlanoAgendadaDto? = null) =
        AssinaturaResposta(Uuid.random(), plano, status, 4, null, null, pagamento, listOf(basico, consultorio, plus, soMensal), periodicidade, 9, exibe, troca)

    @Test fun precoPorPeriodicidadeEMensalEquivalente() {
        assertEquals(29.9, precoDe(basico, Periodicidade.MENSAL)); assertEquals(299.0, precoDe(basico, Periodicidade.ANUAL))
        assertNull(precoDe(soMensal, Periodicidade.ANUAL))
        assertEquals(299.0 / 12, mensalEquivalente(basico)!!, 0.0001); assertNull(mensalEquivalente(soMensal))
        assertTrue(temAnual(listOf(soMensal, basico))); assertFalse(temAnual(listOf(soMensal)))
    }

    @Test fun mesesGratisVemDosPrecosDoServidorENuncaExagera() {
        assertEquals(2, mesesGratisNoAnual(listOf(basico, consultorio, plus)))
        assertEquals(1, mesesGratisNoAnual(listOf(basico, PlanoResposta("X", "X", 100.0, 5, 1050.0))))
        assertNull(mesesGratisNoAnual(listOf(PlanoResposta("X", "X", 100.0, 5, 1180.0))))
        assertNull(mesesGratisNoAnual(listOf(soMensal)))
    }

    @Test fun usoEPertoDoLimite() {
        assertEquals(0.9f, fracaoUso(18, 20)!!, 0.001f); assertNull(fracaoUso(500, null)); assertEquals(1f, fracaoUso(30, 20))
        assertTrue(pertoDoLimite(18, 20)); assertFalse(pertoDoLimite(17, 20)); assertFalse(pertoDoLimite(1000, null))
    }

    @Test fun acaoNoTesteENaAtiva() {
        val teste = assinatura()
        assertEquals(AcaoPlano.ASSINAR, acaoPara(teste, consultorio, Periodicidade.ANUAL))
        assertEquals(AcaoPlano.INDISPONIVEL, acaoPara(teste, soMensal, Periodicidade.ANUAL))
        val ativa = assinatura(StatusAssinatura.ATIVA, basico, Periodicidade.MENSAL)
        assertEquals(AcaoPlano.SEU_PLANO, acaoPara(ativa, basico, Periodicidade.MENSAL))
        assertEquals(AcaoPlano.MUDAR, acaoPara(ativa, basico, Periodicidade.ANUAL))
        assertEquals(AcaoPlano.MUDAR, acaoPara(ativa, consultorio, Periodicidade.MENSAL))
        val comTroca = assinatura(StatusAssinatura.ATIVA, consultorio, Periodicidade.MENSAL, troca = TrocaPlanoAgendadaDto("BASICO", Periodicidade.MENSAL, LocalDate(2026, 12, 5)))
        assertEquals(AcaoPlano.MANTER_ATUAL, acaoPara(comTroca, consultorio, Periodicidade.MENSAL))
    }

    @Test fun chaveDoServidorEPagamentoDesligamOBotao() {
        assertEquals(AcaoPlano.INDISPONIVEL, acaoPara(assinatura(exibe = false), basico, Periodicidade.MENSAL))
        assertEquals(AcaoPlano.INDISPONIVEL, acaoPara(assinatura(pagamento = false), basico, Periodicidade.MENSAL))
        assertTrue(exibePreco(assinatura(exibe = null))); assertFalse(exibePreco(assinatura(exibe = false)))
    }

    @Test fun periodicidadeAtualEPlanoInicial() {
        assertEquals(Periodicidade.MENSAL, periodicidadeAtual(assinatura(periodicidade = null)))
        assertEquals(Periodicidade.MENSAL, periodicidadeAtual(assinatura(periodicidade = Periodicidade.OUTRO)))
        assertEquals(Periodicidade.ANUAL, periodicidadeAtual(assinatura(periodicidade = Periodicidade.ANUAL)))
        assertEquals("BASICO", planoInicial(assinatura()))
        assertEquals("CONSULTORIO", planoInicial(assinatura(StatusAssinatura.ATIVA, consultorio)))
    }

    @Test fun avisoDeFimDoTesteNosUltimos7DiasDoCalendario() {
        val fuso = TimeZone.of("America/Sao_Paulo")
        fun resumo(fim: String, status: StatusAssinatura = StatusAssinatura.TESTE) = AssinaturaResumoResposta(Uuid.random(), "TESTE", status, 5, 4, Instant.parse(fim))
        val agora = Instant.parse("2026-11-17T13:00:00Z") // 10:00 em São Paulo
        assertEquals(1, diasParaAvisoFimTeste(resumo("2026-11-18T12:00:00Z"), agora, fuso))
        assertEquals(0, diasParaAvisoFimTeste(resumo("2026-11-17T23:00:00Z"), agora, fuso))
        assertEquals(7, diasParaAvisoFimTeste(resumo("2026-11-24T12:00:00Z"), agora, fuso))
        assertNull(diasParaAvisoFimTeste(resumo("2026-11-25T12:00:00Z"), agora, fuso))
        assertNull(diasParaAvisoFimTeste(resumo("2026-11-16T12:00:00Z"), agora, fuso))
        assertNull(diasParaAvisoFimTeste(resumo("2026-11-18T12:00:00Z", StatusAssinatura.ATIVA), agora, fuso))
        assertNull(diasParaAvisoFimTeste(null, agora, fuso))
    }
}
