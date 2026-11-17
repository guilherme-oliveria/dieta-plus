package org.br.dietamais.ui.nutri.dieta

import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.sync.novoIdLocal
import org.br.dietamais.dominio.dieta.TotaisCent
import kotlin.uuid.Uuid

enum class MensagemDieta {
    TITULO_OBRIGATORIO, NOME_REFEICAO_OBRIGATORIO, HORARIO_INVALIDO, PORCOES_INVALIDAS, LIMITE_EXCEDIDO, SEM_CONEXAO, CONFLITO, ASSINATURA_INATIVA,
    NAO_ENCONTRADA, DIETA_VAZIA, NAO_EDITAVEL, ALIMENTO_INATIVO, TAXA_EXCEDIDA, ERRO, SALVA, PUBLICADA, DUPLICADA, EDITAR_NO_PLANEJADOR,
    /** MOB-29b: gravada no aparelho; vai ao servidor quando houver conexão. */
    SALVA_NO_APARELHO, PENDENTE_DE_ENVIO, DESCARTADA,
}

data class ItemEdicao(val chave: Uuid = novoIdLocal(), val alimentoId: Uuid, val porcoes: String = "1")
data class RefeicaoEdicao(val chave: Uuid = novoIdLocal(), val nome: String = "", val horario: String = "08:00", val itens: List<ItemEdicao> = emptyList())

/** Dieta em edição. [versao] nula = ainda não existe no servidor; [chave] mantém a idempotência da criação. */
data class RascunhoDieta(
    val dietaId: Uuid = novoIdLocal(), val versao: Long? = null, val status: StatusDieta = StatusDieta.RASCUNHO,
    val titulo: String = "", val orientacao: String = "", val refeicoes: List<RefeicaoEdicao> = emptyList(),
    val chave: Uuid = novoIdLocal(), val alterado: Boolean = false,
) { val editavel get() = status == StatusDieta.RASCUNHO }

data class EstadoDieta(
    val pacienteId: Uuid? = null, val dietas: List<DietaResposta> = emptyList(), val rascunho: RascunhoDieta? = null,
    val grupos: List<GrupoAlimentoResposta> = emptyList(), val alimentos: List<AlimentoResposta> = emptyList(),
    val seletorRefeicao: Uuid? = null, val busca: String = "", val grupoFiltro: Uuid? = null,
    val totais: TotaisCent = TotaisCent(), val totaisRefeicao: Map<Uuid, TotaisCent> = emptyMap(),
    val errosCampos: Map<String, MensagemDieta> = emptyMap(), val carregando: Boolean = false, val enviando: Boolean = false,
    val semConexao: Boolean = false, val confirmarPublicar: Boolean = false, val publicarDaLista: DietaResposta? = null, val erro: MensagemDieta? = null, val aviso: MensagemDieta? = null,
    /** MOB-29b: [escritaOffline] = salvar rascunho funciona sem rede; [pendentes] ainda não confirmadas pelo servidor; [soLocais] nunca saíram do aparelho. */
    val escritaOffline: Boolean = false, val pendentes: Set<Uuid> = emptySet(), val soLocais: Set<Uuid> = emptySet(),
) {
    /** Ações que só existem no servidor (publicar, duplicar, PDF, modelos). */
    val online get() = !semConexao && !enviando && !carregando
    /** Salvar o rascunho: offline também, se o aparelho guarda a escrita. */
    val podeSalvar get() = !enviando && !carregando && (!semConexao || escritaOffline)
    fun pendente(id: Uuid) = id in pendentes
    override fun toString() = "EstadoDieta[conteúdo clínico protegido]"
}
