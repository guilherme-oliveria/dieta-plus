package org.br.dietamais.ui.nutri.assinatura

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.AntecedenciaLembrete
import org.br.dietamais.data.remoto.dto.PreferenciasAgendaDto
import org.br.dietamais.data.repositorio.PortaPreferenciasAgenda

enum class MensagemLembrete { SEM_CONEXAO, ASSINATURA_INATIVA, ERRO, SALVO }

/** Começa no padrão do servidor (1 dia antes, sem lembrete extra) até a leitura chegar. Valor desconhecido também cai no padrão. */
data class EstadoLembreteConsultas(val carregado: Boolean = false, val antecedencia: AntecedenciaLembrete = AntecedenciaLembrete.UM_DIA,
    val extraUmaHora: Boolean = false, val alterado: Boolean = false, val enviando: Boolean = false, val semConexao: Boolean = false,
    val erro: MensagemLembrete? = null, val aviso: MensagemLembrete? = null)

/** "Lembrete das consultas" na conta da nutri (MOB-86, RN-AGE-05, D-08): vale para ela e para os pacientes dela. Exige rede. */
class LembreteConsultasViewModel(private val repositorio: PortaPreferenciasAgenda, private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    conectividade: Flow<Boolean>? = null) : ViewModel() {
    private val atual = MutableStateFlow(EstadoLembreteConsultas())
    val estado = atual.asStateFlow()

    init {
        conectividade?.let { fluxo -> viewModelScope.launch(dispatcher) { fluxo.collect { online -> atual.update { it.copy(semConexao = !online) } } } }
        recarregar()
    }

    fun recarregar() = executar { aplicar(repositorio.obter()) }
    private fun aplicar(p: PreferenciasAgendaDto) = atual.update {
        it.copy(carregado = true, antecedencia = p.antecedencia.takeIf { a -> a != AntecedenciaLembrete.OUTRO } ?: AntecedenciaLembrete.UM_DIA,
            extraUmaHora = p.lembreteExtraUmaHora, alterado = false)
    }

    fun mudarAntecedencia(valor: AntecedenciaLembrete) {
        if (valor == AntecedenciaLembrete.OUTRO) return
        atual.update { it.copy(antecedencia = valor, alterado = true, aviso = null) }
    }
    fun mudarExtraUmaHora(valor: Boolean) = atual.update { it.copy(extraUmaHora = valor, alterado = true, aviso = null) }

    fun salvar() {
        val e = atual.value
        if (e.enviando || !e.alterado || !e.carregado) return
        executar { aplicar(repositorio.salvar(PreferenciasAgendaDto(e.antecedencia, e.extraUmaHora))); atual.update { it.copy(aviso = MensagemLembrete.SALVO) } }
    }

    private fun executar(bloco: suspend () -> Unit) {
        viewModelScope.launch(dispatcher) {
            if (atual.value.semConexao) { atual.update { it.copy(erro = MensagemLembrete.SEM_CONEXAO) }; return@launch }
            atual.update { it.copy(enviando = true, erro = null) }
            try { bloco(); atual.update { it.copy(enviando = false) } }
            catch (c: CancellationException) { throw c }
            catch (erro: ErroApi) {
                atual.update { it.copy(enviando = false, erro = if (erro.codigo == "ASSINATURA_INATIVA") MensagemLembrete.ASSINATURA_INATIVA else MensagemLembrete.ERRO) }
            } catch (_: FalhaComunicacao) { atual.update { it.copy(enviando = false, erro = MensagemLembrete.SEM_CONEXAO) } }
            catch (_: SessaoAlterada) { atual.update { it.copy(enviando = false) } }
            catch (_: Exception) { atual.update { it.copy(enviando = false, erro = MensagemLembrete.ERRO) } }
        }
    }
}
