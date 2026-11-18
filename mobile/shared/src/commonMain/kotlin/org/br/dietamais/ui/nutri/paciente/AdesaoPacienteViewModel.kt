package org.br.dietamais.ui.nutri.paciente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.AdesaoDto
import org.br.dietamais.data.repositorio.PortaAdesao
import kotlin.uuid.Uuid

enum class MensagemAdesao { SEM_CONEXAO, ERRO }

data class EstadoAdesao(val pacienteId: Uuid? = null, val carregando: Boolean = false, val adesao: AdesaoDto? = null,
    val semConexao: Boolean = false, val erro: MensagemAdesao? = null)

/** MOB-89: adesão, humor da semana e registros fora do plano na ficha. Leitura online; sem rede mostra o aviso e tenta de novo ao voltar. */
class AdesaoPacienteViewModel(private val repositorio: PortaAdesao, private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    conectividade: Flow<Boolean>? = null) : ViewModel() {
    private val atual = MutableStateFlow(EstadoAdesao())
    val estado = atual.asStateFlow()
    private var carga: Job? = null

    init {
        conectividade?.let { fluxo -> viewModelScope.launch(dispatcher) {
            fluxo.collect { online ->
                val antes = atual.value
                atual.update { it.copy(semConexao = !online) }
                if (online && antes.semConexao && antes.adesao == null && antes.pacienteId != null) recarregar()
            }
        } }
    }

    /** Troca de paciente limpa o que era do anterior antes de buscar. */
    fun abrir(pacienteId: Uuid) {
        if (atual.value.pacienteId == pacienteId && (atual.value.adesao != null || atual.value.carregando)) return
        atual.update { EstadoAdesao(pacienteId = pacienteId, semConexao = it.semConexao) }
        recarregar()
    }

    fun recarregar() {
        val id = atual.value.pacienteId ?: return
        carga?.cancel()
        if (atual.value.semConexao) { atual.update { it.copy(erro = MensagemAdesao.SEM_CONEXAO, carregando = false) }; return }
        atual.update { it.copy(carregando = true, erro = null) }
        carga = viewModelScope.launch(dispatcher) {
            try {
                val a = repositorio.obter(id)
                atual.update { if (it.pacienteId == id) it.copy(adesao = a, carregando = false) else it }
            } catch (c: CancellationException) { throw c }
            catch (_: FalhaComunicacao) { falhou(id, MensagemAdesao.SEM_CONEXAO) }
            catch (_: SessaoAlterada) { atual.update { it.copy(carregando = false) } }
            catch (_: Exception) { falhou(id, MensagemAdesao.ERRO) }
        }
    }

    private fun falhou(id: Uuid, m: MensagemAdesao) = atual.update { if (it.pacienteId == id) it.copy(carregando = false, erro = m) else it }
}
