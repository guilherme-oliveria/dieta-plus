package org.br.dietamais.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.br.dietamais.data.sync.novoIdLocal
import org.br.dietamais.dominio.prescricoes.SecaoPrescricaoPaciente
import kotlin.uuid.Uuid

data class PedidoNavegacao(val id: Uuid, val rota: String)

/** Pedidos de navegação por `rota` de aviso (toque na notificação ou num item da central). A navegação os consome quando há perfil. */
object SolicitacoesNavegacao {
    private val atual = MutableStateFlow<PedidoNavegacao?>(null)
    val pendente: StateFlow<PedidoNavegacao?> = atual.asStateFlow()
    fun pedir(rota: String) { if (rota.startsWith("/") && rota.length <= 200) atual.value = PedidoNavegacao(novoIdLocal(), rota) }
    fun consumir(id: Uuid) { atual.update { if (it?.id == id) null else it } }
}

/** MOB-93: seção da aba Dieta do paciente pedida por um aviso (push "nova orientação"); a aba a abre e consome o pedido. */
object SolicitacoesSecaoDieta {
    private val atual = MutableStateFlow<SecaoPrescricaoPaciente?>(null)
    val pendente: StateFlow<SecaoPrescricaoPaciente?> = atual.asStateFlow()
    fun pedir(secao: SecaoPrescricaoPaciente) { atual.value = secao }
    fun consumir(secao: SecaoPrescricaoPaciente) { atual.update { if (it == secao) null else it } }
}
