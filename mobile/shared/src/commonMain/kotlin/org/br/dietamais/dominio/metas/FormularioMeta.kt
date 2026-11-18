package org.br.dietamais.dominio.metas

import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.agenda.formatarData
import org.br.dietamais.dominio.agenda.lerData

/** Tipos que a nutri cria à mão: a água nasce com o paciente (RN-MET-05). */
val TIPOS_CRIAVEIS = listOf(TipoMeta.ATIVIDADE_MIN, TipoMeta.PASSOS, TipoMeta.SONO_HORAS, TipoMeta.FRUTAS_VERDURAS, TipoMeta.SIM_NAO, TipoMeta.CONTADOR)

/** RN-MET-01: nome (e unidade) só em SIM_NAO e CONTADOR. */
fun tipoLivre(tipo: TipoMeta) = tipo == TipoMeta.SIM_NAO || tipo == TipoMeta.CONTADOR

enum class ErroMeta { NOME, ALVO, VEZES, INICIO, FIM }

const val LIMITE_NOME_META = 80
const val LIMITE_UNIDADE_META = 20

/**
 * MOB-91: formulário de nova meta ou de edição. [metaId] e [versao] só na edição; na água só o alvo muda.
 * [alvo], [inicio] e [fim] como a nutri digitou (vírgula decimal, dd/mm/aaaa).
 */
data class FormularioMeta(val tipo: TipoMeta = TipoMeta.ATIVIDADE_MIN, val nome: String = "", val alvo: String = "", val unidade: String = "",
    val frequencia: FrequenciaMeta = FrequenciaMeta.DIARIA, val vezesPorSemana: Int = 3, val inicio: String = "", val fim: String = "",
    val metaId: kotlin.uuid.Uuid? = null, val versao: Long? = null) {
    val edicao get() = metaId != null
}

fun novoFormularioMeta(hoje: LocalDate) = FormularioMeta(inicio = formatarData(hoje))

fun formularioDe(meta: MetaResposta) = FormularioMeta(meta.tipo, meta.nome.orEmpty(), textoAlvo(meta.alvo), meta.unidade.orEmpty(),
    meta.frequencia.takeIf { it != FrequenciaMeta.OUTRO } ?: FrequenciaMeta.DIARIA, meta.vezesPorSemana ?: 3,
    meta.inicio?.let(::formatarData).orEmpty(), meta.fim?.let(::formatarData).orEmpty(), meta.id, meta.versao)

/** 2.0 vira "2", 7.5 vira "7,5". */
fun textoAlvo(valor: Double): String = if (valor == kotlin.math.floor(valor) && valor < 1e15) valor.toLong().toString() else valor.toString().replace('.', ',')

fun lerAlvo(texto: String): Double? = texto.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 1_000_000 && it.isFinite() }

/** Feedback de RN-MET-01 (o backend decide). SIM_NAO tem alvo 1 e não pede o campo. */
fun validarMeta(f: FormularioMeta): Set<ErroMeta> {
    val erros = mutableSetOf<ErroMeta>()
    if (tipoLivre(f.tipo) && (f.nome.isBlank() || f.nome.trim().length > LIMITE_NOME_META)) erros += ErroMeta.NOME
    if (f.tipo != TipoMeta.SIM_NAO && lerAlvo(f.alvo) == null) erros += ErroMeta.ALVO
    if (f.frequencia == FrequenciaMeta.SEMANAL && f.vezesPorSemana !in 1..7) erros += ErroMeta.VEZES
    val inicio = f.inicio.takeIf { it.isNotBlank() }?.let(::lerData)
    if (f.inicio.isNotBlank() && inicio == null) erros += ErroMeta.INICIO
    val fim = f.fim.takeIf { it.isNotBlank() }?.let(::lerData)
    if (f.fim.isNotBlank() && fim == null) erros += ErroMeta.FIM
    if (inicio != null && fim != null && fim < inicio) erros += ErroMeta.FIM
    return erros
}

private fun alvoDe(f: FormularioMeta) = if (f.tipo == TipoMeta.SIM_NAO) 1.0 else lerAlvo(f.alvo) ?: 0.0
private fun nomeDe(f: FormularioMeta) = f.nome.trim().takeIf { tipoLivre(f.tipo) && it.isNotEmpty() }
private fun unidadeDe(f: FormularioMeta) = f.unidade.trim().take(LIMITE_UNIDADE_META).takeIf { f.tipo == TipoMeta.CONTADOR && it.isNotEmpty() }
private fun vezesDe(f: FormularioMeta) = f.vezesPorSemana.takeIf { f.frequencia == FrequenciaMeta.SEMANAL }

fun requisicaoMeta(f: FormularioMeta, id: kotlin.uuid.Uuid) = MetaRequisicao(id, f.tipo, nomeDe(f), alvoDe(f), unidadeDe(f), f.frequencia, vezesDe(f),
    f.inicio.takeIf { it.isNotBlank() }?.let(::lerData), f.fim.takeIf { it.isNotBlank() }?.let(::lerData))

/** PATCH: na água só o alvo; nas outras, tudo que o formulário tem. Fim apagado não limpa no servidor (campo nulo não muda). */
fun atualizacaoMeta(f: FormularioMeta) = if (f.tipo == TipoMeta.AGUA) AtualizarMetaRequisicao(alvo = alvoDe(f))
    else AtualizarMetaRequisicao(nomeDe(f), alvoDe(f), unidadeDe(f), f.frequencia, vezesDe(f),
        f.inicio.takeIf { it.isNotBlank() }?.let(::lerData), f.fim.takeIf { it.isNotBlank() }?.let(::lerData))

/** Item de modelo a partir de uma meta do paciente; a água fica de fora (RN-MET-04). */
fun itensDeModelo(metas: List<MetaResposta>): List<ItemModeloMeta> = metas.filter { it.tipo != TipoMeta.AGUA && it.tipo != TipoMeta.OUTRO && it.frequencia != FrequenciaMeta.OUTRO }
    .map { ItemModeloMeta(it.tipo, it.nome, it.alvo, it.unidade, it.frequencia, it.vezesPorSemana) }

/** Metas em vigor hoje (sem fim ou com fim de hoje em diante), água primeiro, depois pela criação. */
fun metasAtivas(metas: List<MetaResposta>, hoje: LocalDate): List<MetaResposta> =
    metas.filter { it.fim == null || it.fim >= hoje }.sortedWith(compareBy({ it.tipo != TipoMeta.AGUA }, { it.criadoEm }))

/** O artboard pinta de laranja o cumprimento abaixo de 50% na semana. */
fun abaixoDaMetade(percentual: Double?) = percentual != null && percentual < 50.0
