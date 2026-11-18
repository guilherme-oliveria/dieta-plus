package org.br.dietamais.ui.nutri.assinatura

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.PortaAssinatura
import org.br.dietamais.dominio.assinatura.*

enum class MensagemAssinatura { SEM_CONEXAO, JA_ATIVA, PAGAMENTO_INDISPONIVEL, PLANO_INDISPONIVEL, ERRO }

data class EstadoAssinatura(val carregado: Boolean = false, val assinatura: AssinaturaResposta? = null, val enviando: Boolean = false,
    val semConexao: Boolean = false, val erro: MensagemAssinatura? = null, val urlCheckout: String? = null,
    /** Mensal/Anual da comparação (MOB-96) e o plano marcado. */
    val periodicidade: Periodicidade = Periodicidade.MENSAL, val planoEscolhido: String? = null,
    /** Descida agendada que acabou de ser pedida (resposta do checkout sem URL). */
    val trocaAgendadaPara: LocalDate? = null) {
    /** RN-ASS-04: sem assinatura ativa a nutri só lê. */
    val somenteLeitura get() = assinatura?.status.let { it == StatusAssinatura.CANCELADA || it == StatusAssinatura.EXPIRADA }
    val emAtraso get() = assinatura?.status == StatusAssinatura.EM_ATRASO
    val plano get() = assinatura?.planosDisponiveis?.firstOrNull { it.codigo == planoEscolhido }
    val acao get() = assinatura?.let { a -> plano?.let { acaoPara(a, it, periodicidade) } } ?: AcaoPlano.INDISPONIVEL
    override fun toString() = "EstadoAssinatura[protegido]"
}

/** Plano, uso e comparação de planos (MOB-32, MOB-96). O app nunca vende: abre o checkout hospedado no navegador (RN-ASS-06). */
class AssinaturaViewModel(private val repositorio: PortaAssinatura, private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    conectividade: Flow<Boolean>? = null) : ViewModel() {
    private val atual = MutableStateFlow(EstadoAssinatura())
    val estado = atual.asStateFlow()

    init {
        conectividade?.let { fluxo -> viewModelScope.launch(dispatcher) { fluxo.collect { online -> atual.update { it.copy(semConexao = !online) } } } }
        recarregar()
    }

    fun recarregar() = executar { aplicar(repositorio.obter()) }

    /** Na primeira leitura, a comparação abre na periodicidade e no plano atuais; depois mantém o que a nutri escolheu. */
    private fun aplicar(a: AssinaturaResposta) = atual.update { e ->
        val primeira = e.assinatura == null
        val periodicidade = if (primeira) periodicidadeAtual(a).takeIf { it == Periodicidade.MENSAL || temAnual(a.planosDisponiveis) } ?: Periodicidade.MENSAL else e.periodicidade
        val escolhido = e.planoEscolhido?.takeIf { c -> a.planosDisponiveis.any { it.codigo == c } } ?: planoInicial(a)
        e.copy(carregado = true, assinatura = a, periodicidade = periodicidade, planoEscolhido = escolhido)
    }

    fun mudarPeriodicidade(p: Periodicidade) {
        if (p == Periodicidade.OUTRO) return
        if (p == Periodicidade.ANUAL && atual.value.assinatura?.planosDisponiveis?.let(::temAnual) != true) return
        atual.update { it.copy(periodicidade = p, erro = null, trocaAgendadaPara = null) }
    }

    fun escolherPlano(codigo: String) {
        if (atual.value.assinatura?.planosDisponiveis?.any { it.codigo == codigo } != true) return
        atual.update { it.copy(planoEscolhido = codigo, erro = null, trocaAgendadaPara = null) }
    }

    /** Assina, sobe, desce ou desfaz a troca agendada, conforme [acaoPara]. Quem decide de verdade é o servidor. */
    fun confirmar() {
        val e = atual.value
        val plano = e.plano ?: return
        if (e.acao !in setOf(AcaoPlano.ASSINAR, AcaoPlano.MUDAR, AcaoPlano.MANTER_ATUAL)) return
        executar {
            val resposta = repositorio.pedirCheckout(plano.codigo, e.periodicidade)
            val url = resposta.url
            when {
                url != null -> { if (!url.startsWith("https://")) throw RespostaInvalida(); atual.update { it.copy(urlCheckout = url) } }
                else -> { atual.update { it.copy(trocaAgendadaPara = resposta.trocaAgendadaPara) }; aplicar(repositorio.obter()) }
            }
        }
    }

    /** Atalho da 1.3 (um botão por plano, sempre mensal). */
    fun assinar(plano: String) { escolherPlano(plano); if (atual.value.planoEscolhido == plano) confirmar() }

    fun urlAberta() = atual.update { it.copy(urlCheckout = null) }

    private fun executar(bloco: suspend () -> Unit) {
        viewModelScope.launch(dispatcher) {
            if (atual.value.semConexao) { atual.update { it.copy(erro = MensagemAssinatura.SEM_CONEXAO) }; return@launch }
            if (atual.value.enviando) return@launch
            atual.update { it.copy(enviando = true, erro = null) }
            try { bloco(); atual.update { it.copy(enviando = false) } }
            catch (c: CancellationException) { throw c }
            catch (erro: ErroApi) {
                val msg = when (erro.codigo) {
                    "ASSINATURA_JA_ATIVA" -> MensagemAssinatura.JA_ATIVA
                    "PAGAMENTO_INDISPONIVEL" -> MensagemAssinatura.PAGAMENTO_INDISPONIVEL
                    "PLANO_INDISPONIVEL" -> MensagemAssinatura.PLANO_INDISPONIVEL
                    else -> MensagemAssinatura.ERRO
                }
                atual.update { it.copy(enviando = false, erro = msg) }
                if (msg == MensagemAssinatura.JA_ATIVA || msg == MensagemAssinatura.PLANO_INDISPONIVEL) try { aplicar(repositorio.obter()) } catch (_: Exception) { }
            } catch (_: FalhaComunicacao) { atual.update { it.copy(enviando = false, erro = MensagemAssinatura.SEM_CONEXAO) } }
            catch (_: SessaoAlterada) { atual.update { it.copy(enviando = false) } }
            catch (_: Exception) { atual.update { it.copy(enviando = false, erro = MensagemAssinatura.ERRO) } }
        }
    }
}
