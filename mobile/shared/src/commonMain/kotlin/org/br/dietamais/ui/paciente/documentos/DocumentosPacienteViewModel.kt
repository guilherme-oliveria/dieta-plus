package org.br.dietamais.ui.paciente.documentos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.br.dietamais.data.remoto.FalhaComunicacao
import org.br.dietamais.data.remoto.dto.DocumentoPacienteResposta
import org.br.dietamais.data.repositorio.PortaDocumentosPaciente

data class EstadoDocumentosPaciente(val carregado: Boolean = false, val documentos: List<DocumentoPacienteResposta> = emptyList(),
    val semConexao: Boolean = false, val erro: Boolean = false)

/** Mais novos primeiro; anulados continuam na lista, marcados (RN-DOC-03). */
fun documentosDoPaciente(lista: List<DocumentoPacienteResposta>) =
    lista.sortedWith(compareByDescending<DocumentoPacienteResposta> { it.emitidoEm }.thenBy { it.id.toString() })

/** MOB-95 (H-06): lista dos documentos do paciente, offline no app; abrir o PDF exige rede. */
class DocumentosPacienteViewModel(private val porta: PortaDocumentosPaciente, private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    conectividade: Flow<Boolean>? = null) : ViewModel() {
    private val atual = MutableStateFlow(EstadoDocumentosPaciente())
    val estado = atual.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            porta.observar().catch { atual.update { it.copy(carregado = true, erro = true) } }
                .collect { lista -> atual.update { it.copy(documentos = documentosDoPaciente(lista), carregado = true, erro = false) } }
        }
        conectividade?.let { fluxo -> viewModelScope.launch(dispatcher) {
            fluxo.collect { online ->
                val antes = atual.value.semConexao
                atual.update { it.copy(semConexao = !online) }
                if (online && antes) atualizar()
            }
        } }
        atualizar()
    }

    fun atualizar() {
        viewModelScope.launch(dispatcher) {
            try { porta.atualizar() }
            catch (c: CancellationException) { throw c }
            catch (falha: Exception) { atual.update { it.copy(semConexao = it.semConexao || falha is FalhaComunicacao, erro = falha !is FalhaComunicacao) } }
        }
    }
}
