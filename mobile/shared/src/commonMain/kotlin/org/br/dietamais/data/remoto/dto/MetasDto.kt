package org.br.dietamais.data.remoto.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable(with = TipoMetaSerializer::class)
enum class TipoMeta { AGUA, ATIVIDADE_MIN, PASSOS, SONO_HORAS, FRUTAS_VERDURAS, SIM_NAO, CONTADOR, OUTRO }
object TipoMetaSerializer : EnumComOutro<TipoMeta>("TipoMeta", TipoMeta.entries.toTypedArray(), TipoMeta.OUTRO)

@Serializable(with = FrequenciaMetaSerializer::class)
enum class FrequenciaMeta { DIARIA, SEMANAL, OUTRO }
object FrequenciaMetaSerializer : EnumComOutro<FrequenciaMeta>("FrequenciaMeta", FrequenciaMeta.entries.toTypedArray(), FrequenciaMeta.OUTRO)

/**
 * Meta de hábito (BE-74a, RN-MET-01 a 05). A nutri escreve online; o paciente lê pelo sync (coleção `metas`).
 * `nome` e `unidade` só em `SIM_NAO` e `CONTADOR`. `alvoPadrao` só na água: true enquanto o alvo segue 35 ml × peso.
 */
@Serializable
data class MetaResposta(val id: Uuid, val pacienteId: Uuid, val tipo: TipoMeta, val nome: String? = null, val alvo: Double, val unidade: String? = null,
    val frequencia: FrequenciaMeta, val vezesPorSemana: Int? = null, val inicio: LocalDate? = null, val fim: LocalDate? = null,
    val alvoPadrao: Boolean? = null, val versao: Long, val criadoEm: Instant, val atualizadoEm: Instant)

@Serializable
data class MetasResposta(val itens: List<MetaResposta> = emptyList())

@Serializable
data class MetaRequisicao(val id: Uuid? = null, val tipo: TipoMeta, val nome: String? = null, val alvo: Double, val unidade: String? = null,
    val frequencia: FrequenciaMeta, val vezesPorSemana: Int? = null, val inicio: LocalDate? = null, val fim: LocalDate? = null)

/** `PATCH /metas/{id}` com `If-Match`: campo nulo não muda; `encerrar` sozinho fecha a meta hoje (a de água não se encerra). */
@Serializable
data class AtualizarMetaRequisicao(val nome: String? = null, val alvo: Double? = null, val unidade: String? = null, val frequencia: FrequenciaMeta? = null,
    val vezesPorSemana: Int? = null, val inicio: LocalDate? = null, val fim: LocalDate? = null, val encerrar: Boolean? = null)

/** Registro do paciente numa meta (RN-MET-02): só inclusão, pelo REST ou pela coleção `registrosMeta` do sync. Na água, `valor` é ml inteiro de 1 a 5.000. */
@Serializable
data class RegistroMetaRequisicao(val id: Uuid? = null, val metaId: Uuid, val data: LocalDate, val valor: Double, val registradoEm: Instant)
@Serializable
data class RegistroMetaResposta(val id: Uuid, val metaId: Uuid, val data: LocalDate, val valor: Double, val registradoEm: Instant)

@Serializable
data class ItemModeloMeta(val tipo: TipoMeta, val nome: String? = null, val alvo: Double, val unidade: String? = null,
    val frequencia: FrequenciaMeta, val vezesPorSemana: Int? = null)
@Serializable
data class ModeloMetaRequisicao(val nome: String, val itens: List<ItemModeloMeta>)
@Serializable
data class ModeloMetaResposta(val id: Uuid, val nome: String, val itens: List<ItemModeloMeta> = emptyList(), val versao: Long, val criadoEm: Instant, val atualizadoEm: Instant)
@Serializable
data class ModelosMetaResposta(val itens: List<ModeloMetaResposta> = emptyList())

/** Cumprimento calculado pelo servidor (RN-MET-03, mesmos vetores de `metas.json`). */
@Serializable
data class CumprimentoMeta(val metaId: Uuid, val tipo: TipoMeta, val nome: String? = null, val diasPrevistos: Int, val diasCumpridos: Int,
    val percentual: Double? = null, val sequenciaAtual: Int, val melhorSequencia: Int)
@Serializable
data class CumprimentoMetasResposta(val de: LocalDate, val ate: LocalDate, val metas: List<CumprimentoMeta> = emptyList())
