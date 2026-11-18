package org.br.dietamais.ui.nutri.dieta

import org.br.dietamais.data.remoto.dto.ModeloDietaResposta
import kotlin.uuid.Uuid

enum class MensagemModelos { SEM_CONEXAO, NAO_ENCONTRADA, SOMENTE_LEITURA, CONFLITO, LIMITE, ASSINATURA_INATIVA, TAXA_EXCEDIDA, ERRO, DUPLICADO, APAGADO, SALVO, PENDENTE_DE_ENVIO }

enum class AbaModelos { SISTEMA, MEUS }

/** [apagar] = modelo à espera da confirmação de exclusão. [escritaOffline]/[pendentes]: MOB-29d, os modelos dela gravam no aparelho. */
data class EstadoModelos(
    val modelos: List<ModeloDietaResposta> = emptyList(), val aba: AbaModelos = AbaModelos.SISTEMA, val apagar: ModeloDietaResposta? = null,
    val carregando: Boolean = false, val enviando: Boolean = false, val semConexao: Boolean = false, val carregado: Boolean = false,
    val erro: MensagemModelos? = null, val aviso: MensagemModelos? = null,
    val escritaOffline: Boolean = false, val pendentes: Set<Uuid> = emptySet(),
) {
    private val livre: Boolean get() = !enviando && !carregando
    /** Criar, editar e apagar os dela: com rede ou gravando no aparelho. */
    val podeEscrever: Boolean get() = livre && (!semConexao || escritaOffline)
    /** Duplicar o do sistema depende do servidor; o dela vira cópia local. */
    fun podeDuplicar(m: ModeloDietaResposta) = livre && (!semConexao || (escritaOffline && !m.doSistema))
    /** Aplicar a um paciente exige rede e o modelo já aceito pelo servidor. */
    fun podeUsar(m: ModeloDietaResposta) = livre && !semConexao && m.id !in pendentes
    val visiveis: List<ModeloDietaResposta> get() = modelos.filter { it.doSistema == (aba == AbaModelos.SISTEMA) }
    override fun toString() = "EstadoModelos[conteúdo clínico protegido]"
}
