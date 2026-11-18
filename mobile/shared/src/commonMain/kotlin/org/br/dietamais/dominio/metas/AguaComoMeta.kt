package org.br.dietamais.dominio.metas

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.data.remoto.dto.AguaResposta
import org.br.dietamais.data.remoto.dto.MetaResposta
import org.br.dietamais.data.remoto.dto.RegistroMetaResposta
import org.br.dietamais.data.remoto.dto.TipoMeta
import org.br.dietamais.dominio.rotina.metaAguaMl
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/**
 * MOB-99 (RN-MET-05, D-07): a água é uma meta. Cada vínculo ativo tem a sua meta `AGUA` e o servidor copia cada registro de água
 * para todas elas (e para o caminho antigo `agua`), então basta ler uma: a mais antiga que vale [hoje], para a escolha não mudar de um dia para o outro.
 */
fun metaAguaAtual(metas: List<MetaResposta>, hoje: LocalDate): MetaResposta? =
    metas.filter { it.tipo == TipoMeta.AGUA && metaValeEm(it, hoje) }.minWithOrNull(compareBy<MetaResposta> { it.criadoEm }.thenBy { it.id.toString() })

/** Alvo do dia: o da meta de água (que a nutri pode ajustar); sem meta (backend antigo ou sem vínculo ativo), 35 ml × peso (RN-ROT-03). */
fun alvoAguaMl(meta: MetaResposta?, pesoKg: Double?): Int = meta?.alvo?.roundToInt()?.takeIf { it > 0 } ?: metaAguaMl(pesoKg)

/**
 * Água gravada no [dia], sem contar duas vezes o mesmo copo.
 * Com a meta: os registros dela (o servidor já trouxe para ela o histórico do caminho antigo) mais os registros antigos ainda na fila
 * ([aguaNaFila]: gravados antes da atualização e não enviados), que o servidor só espelha na meta quando receber.
 * Sem a meta: só o caminho antigo, como antes da 1.4.0.
 */
fun aguaGravadaMl(meta: MetaResposta?, registrosMeta: List<RegistroMetaResposta>, agua: List<AguaResposta>, aguaNaFila: Set<Uuid>,
    dia: LocalDate, fuso: TimeZone): Int {
    fun doDia(a: AguaResposta) = a.registradoEm.toLocalDateTime(fuso).date == dia
    if (meta == null) return agua.filter(::doDia).sumOf { it.ml }
    val daMeta = registrosMeta.filter { it.metaId == meta.id && it.data == dia }.sumOf { it.valor }.roundToInt()
    return daMeta + agua.filter { it.id in aguaNaFila && doDia(it) }.sumOf { it.ml }
}
