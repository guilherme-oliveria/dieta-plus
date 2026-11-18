package org.br.dietamais.ui.nutri.anamnese

import org.br.dietamais.data.remoto.dto.ModeloQuestionarioResposta
import org.br.dietamais.data.remoto.dto.QuestionarioResposta
import org.br.dietamais.dominio.anamnese.EditorModelo
import org.br.dietamais.dominio.anamnese.ValidacaoModelo
import kotlin.uuid.Uuid

enum class MensagemAnamnese { SEM_CONEXAO, CONFLITO, ASSINATURA_INATIVA, VINCULO_ENCERRADO, NAO_ENCONTRADO, TAXA_EXCEDIDA, MODELO_INVALIDO, ERRO, MODELO_SALVO, ENVIADO,
    /** MOB-29c: modelo salvo no aparelho, vai ao servidor com a internet; e modelo ainda na fila, que não pode ir ao paciente. */
    MODELO_NO_APARELHO, MODELO_PENDENTE,
    FECHADO, PENDENTES, OBRIGATORIAS, ENTRADA_INVALIDA, CONCLUIDO }

data class EstadoAnamneseNutri(val pacienteId: Uuid? = null, val modelos: List<ModeloQuestionarioResposta> = emptyList(), val questionarios: List<QuestionarioResposta> = emptyList(),
    val atualizando: Boolean = false, val enviando: Boolean = false, val semConexao: Boolean = false, val gerenciandoModelos: Boolean = false,
    val editor: EditorModelo? = null, val validacao: ValidacaoModelo = ValidacaoModelo(), val lendoId: Uuid? = null,
    val erro: MensagemAnamnese? = null, val aviso: MensagemAnamnese? = null,
    /** MOB-29c: [escritaOffline] = modelos se criam e editam sem rede; [pendentes] ainda não chegaram ao servidor. */
    val escritaOffline: Boolean = false, val pendentes: Set<Uuid> = emptySet()) {
    val podeEditarModelos: Boolean get() = !enviando && (!semConexao || escritaOffline)
    /** Enviar ao paciente exige rede e o modelo já aceito pelo servidor. */
    fun podeEnviar(id: Uuid) = !enviando && !semConexao && id !in pendentes
    val lendo: QuestionarioResposta? get() = questionarios.firstOrNull { it.id == lendoId }
}
