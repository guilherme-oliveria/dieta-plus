package org.br.dietamais.ui.paciente.dieta

import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.dto.DietaSnapshotResposta
import org.br.dietamais.data.remoto.dto.RefeicaoResposta
import org.br.dietamais.data.remoto.dto.StatusCheckin
import kotlin.uuid.Uuid
import org.br.dietamais.dominio.rotina.*

enum class MensagemDietaPaciente { SEM_CONEXAO, CONFLITO, ERRO }

/** Troca em andamento: [escolhas] mapeia itemId do snapshot -> alimento substituto escolhido (RN-SUB). */
data class EstadoTroca(val refeicaoId: Uuid, val escolhas: Map<Uuid, Uuid> = emptyMap(), val itemId: Uuid? = null)

/** MOB-88: pergunta de fome antes e sensação depois, aberta depois de "Comi" (1 a 5, opcionais). */
data class EstadoHumor(val refeicaoId: Uuid, val nomeRefeicao: String, val fome: Int? = null, val sensacao: Int? = null)

data class EstadoDietaPaciente(
    val carregado: Boolean = false, val dieta: DietaSnapshotResposta? = null, val refeicoes: List<RefeicaoResposta> = emptyList(),
    val proxima: RefeicaoResposta? = null, val alternativas: List<DietaSnapshotResposta> = emptyList(),
    val versaoSelecao: Long = 0, val semConexao: Boolean = false, val enviando: Boolean = false, val erro: MensagemDietaPaciente? = null,
    val refeicaoAberta: Uuid? = null,
    val hoje: LocalDate? = null, val dia: LocalDate? = null, val dias: List<LocalDate> = emptyList(),
    val checkins: Map<Uuid, StatusCheckin> = emptyMap(), val aguaMl: Int = 0, val metaAguaMl: Int = 2000,
    /** MOB-99: sequência e semana da meta de água; nulo sem a meta (backend antigo ou vínculo sem meta). */
    val progressoAgua: org.br.dietamais.dominio.metas.ProgressoMeta? = null, val troca: EstadoTroca? = null,
    val listaAberta: Boolean = false, val gruposLista: List<GrupoListaPaciente> = emptyList(), val buscaLista: String = "",
    val grupoListaFiltro: Uuid? = null, val grupoListaAberto: Uuid? = null,
    /** MOB-UX-04: há um check-in ainda não gravado, que o paciente pode desfazer. */
    val podeDesfazer: Boolean = false,
    /** MOB-UX-05: há lançamento de água ainda não enviado. */
    val podeDesfazerAgua: Boolean = false,
    /** MOB-88 */
    val humor: EstadoHumor? = null, val foraDoPlano: FormularioForaDoPlano? = null, val errosForaDoPlano: Set<ErroForaDoPlano> = emptySet(),
    val podeDesfazerFora: Boolean = false,
) {
    val gruposListaVisiveis get() = filtrarListaPaciente(gruposLista, buscaLista, grupoListaFiltro)
    val ehHoje get() = dia != null && dia == hoje
    override fun toString() = "EstadoDietaPaciente[conteúdo clínico protegido]"
}
