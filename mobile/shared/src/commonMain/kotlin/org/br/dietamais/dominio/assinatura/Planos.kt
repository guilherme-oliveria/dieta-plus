package org.br.dietamais.dominio.assinatura

import org.br.dietamais.data.remoto.dto.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import org.br.dietamais.dominio.agenda.diaLocal
import kotlin.math.floor
import kotlin.time.Instant

/**
 * Comparação de planos (MOB-96, H-07, artboards `A-Assinatura-*`). Todo preço e limite vem do `GET /assinatura`: nada de valor fixo aqui.
 * As regras de verdade (subir na hora, descer no vencimento, limite de pacientes) são do backend (RN-ASS-09 a 11); isto só monta a tela.
 */

/** Aviso de "perto do limite" a partir de 90% das vagas. */
const val FRACAO_PERTO_DO_LIMITE = 0.9

/** O painel avisa o fim do teste nos últimos 7 dias. */
const val DIAS_AVISO_FIM_TESTE = 7

enum class AcaoPlano { SEU_PLANO, ASSINAR, MUDAR, MANTER_ATUAL, INDISPONIVEL }

/** Preço do plano na periodicidade; nulo quando o plano não é vendido assim (anual sem `precoAnual`). */
fun precoDe(plano: PlanoResposta, periodicidade: Periodicidade): Double? = when (periodicidade) {
    Periodicidade.ANUAL -> plano.precoAnual?.takeIf { it > 0 }
    else -> plano.precoMensal.takeIf { it > 0 }
}

/** Quanto o plano anual sai por mês, para comparar com o mensal. */
fun mensalEquivalente(plano: PlanoResposta): Double? = plano.precoAnual?.takeIf { it > 0 }?.let { it / 12 }

/** A alternância Mensal/Anual só aparece se algum plano tem preço anual. */
fun temAnual(planos: List<PlanoResposta>) = planos.any { mensalEquivalente(it) != null }

/**
 * "N meses grátis" do anual, calculado dos preços que o servidor mandou. Usa o plano com menor desconto, para nunca prometer mais do que
 * todos dão; nulo se algum plano anual não economiza ao menos um mês.
 */
fun mesesGratisNoAnual(planos: List<PlanoResposta>): Int? {
    val meses = planos.mapNotNull { p -> p.precoAnual?.takeIf { it > 0 && p.precoMensal > 0 }?.let { (12 * p.precoMensal - it) / p.precoMensal } }
    if (meses.isEmpty()) return null
    return floor(meses.min() + 0.05).toInt().takeIf { it >= 1 }
}

/** Fração das vagas ocupadas (0 a 1); nula quando o plano não tem limite. */
fun fracaoUso(ocupados: Int, limite: Int?): Float? = limite?.let { if (it <= 0) 1f else (ocupados.toFloat() / it).coerceIn(0f, 1f) }

fun pertoDoLimite(ocupados: Int, limite: Int?): Boolean = limite != null && limite > 0 && ocupados >= limite * FRACAO_PERTO_DO_LIMITE

/** Periodicidade em vigor: a do servidor ou, numa resposta da 1.3 sem o campo, mensal. */
fun periodicidadeAtual(a: AssinaturaResposta): Periodicidade = a.periodicidade?.takeIf { it != Periodicidade.OUTRO } ?: Periodicidade.MENSAL

/** D-09: sem a chave (servidor da 1.3), mostra preço e botão como antes. */
fun exibePreco(a: AssinaturaResposta): Boolean = a.exibePrecoEBotao ?: true

/**
 * O que o botão faz para o plano escolhido. Com a assinatura ativa, escolher o próprio plano não faz nada, a não ser que haja troca agendada:
 * aí pedir o plano atual desfaz a troca (BE-81b).
 */
fun acaoPara(a: AssinaturaResposta, plano: PlanoResposta, periodicidade: Periodicidade): AcaoPlano {
    if (!exibePreco(a) || !a.pagamentoDisponivel || precoDe(plano, periodicidade) == null) return AcaoPlano.INDISPONIVEL
    if (a.status != StatusAssinatura.ATIVA) return AcaoPlano.ASSINAR
    val atual = plano.codigo == a.plano.codigo && periodicidade == periodicidadeAtual(a)
    return when {
        !atual -> AcaoPlano.MUDAR
        a.trocaAgendada != null -> AcaoPlano.MANTER_ATUAL
        else -> AcaoPlano.SEU_PLANO
    }
}

/** Plano marcado ao abrir: o atual se ele está à venda, senão o primeiro da lista. */
fun planoInicial(a: AssinaturaResposta): String? =
    a.planosDisponiveis.firstOrNull { it.codigo == a.plano.codigo }?.codigo ?: a.planosDisponiveis.firstOrNull()?.codigo

/** Dias de calendário (no fuso da nutri) até o fim do teste, para o aviso do painel; nulo fora da janela. 0 = termina hoje, 1 = amanhã. */
fun diasParaAvisoFimTeste(assinatura: AssinaturaResumoResposta?, agora: Instant, fuso: TimeZone): Int? {
    if (assinatura?.status != StatusAssinatura.TESTE || assinatura.fimPeriodoEm < agora) return null
    val dias = diaLocal(agora, fuso).daysUntil(diaLocal(assinatura.fimPeriodoEm, fuso))
    return dias.takeIf { it in 0..DIAS_AVISO_FIM_TESTE }
}
