package org.br.dietamais.ui.nutri.paciente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.PortaPacientes
import org.br.dietamais.data.sync.novoIdLocal
import org.br.dietamais.dominio.saude.*
import kotlin.uuid.Uuid

enum class MensagemSaude { SALVO, REMOVIDO, SEM_CONEXAO, ITEM_INVALIDO, ASSINATURA_INATIVA, CONFLITO, ERRO }

data class EstadoSaudePaciente(val paciente: PacienteResposta? = null, val formulario: FormularioItemSaude? = null, val erroFormulario: ErroItemSaude? = null,
    val removendo: Uuid? = null, val enviando: Boolean = false, val erro: MensagemSaude? = null, val aviso: MensagemSaude? = null) {
    val itens: List<RestricaoSaudeDto> get() = RestricoesSaude.ordenar(paciente?.restricoesSaude.orEmpty())
    val encerrado get() = paciente?.status == StatusVinculo.ENCERRADO
    override fun toString() = "EstadoSaudePaciente[conteúdo clínico protegido]"
}

/**
 * Seção "Saúde e restrições" da ficha (MOB-82, RN-SAU-01 e 02). Só a nutri vê. No app a escrita é offline-first pela ficha (MOB-29);
 * na web vai direto à API. A lista inteira vai com a ficha.
 */
class SaudePacienteViewModel(private val pacientes: PortaPacientes, private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val novoId: () -> Uuid = { novoIdLocal() }) : ViewModel() {
    private val atual = MutableStateFlow(EstadoSaudePaciente())
    val estado = atual.asStateFlow()

    /** A ficha vem do cache (ou da API, na web); trocar de paciente fecha o formulário. */
    fun abrir(paciente: PacienteResposta) = atual.update {
        if (it.paciente?.id == paciente.id) it.copy(paciente = paciente)
        else EstadoSaudePaciente(paciente = paciente)
    }

    fun novoItem() { if (podeEditar()) atual.update { it.copy(formulario = FormularioItemSaude(), erroFormulario = null, aviso = null, erro = null) } }
    fun editar(id: Uuid) {
        if (!podeEditar()) return
        val item = atual.value.paciente?.restricoesSaude.orEmpty().firstOrNull { it.id == id } ?: return
        atual.update { it.copy(formulario = RestricoesSaude.formularioDe(item), erroFormulario = null, aviso = null, erro = null) }
    }
    fun cancelar() = atual.update { it.copy(formulario = null, erroFormulario = null) }

    fun mudarTipo(t: TipoRestricaoSaude) = form { f ->
        val marcadores = if (RestricoesSaude.ligaMarcadores(t)) (if (f.marcadoresEscolhidos) f.marcadores else RestricoesSaude.sugerirMarcadores(f.nome)) else emptySet()
        f.copy(tipo = t, marcadores = marcadores)
    }
    fun mudarNome(v: String) = form { f ->
        val nome = v.take(RestricoesSaude.LIMITE_NOME + 20)
        f.copy(nome = nome, marcadores = if (!f.marcadoresEscolhidos && RestricoesSaude.ligaMarcadores(f.tipo)) RestricoesSaude.sugerirMarcadores(nome) else f.marcadores)
    }
    fun mudarGravidade(g: GravidadeRestricao?) = form { it.copy(gravidade = if (it.gravidade == g) null else g) }
    fun mudarObservacao(v: String) = form { it.copy(observacao = v.take(RestricoesSaude.LIMITE_OBSERVACAO + 20)) }
    fun alternarMarcador(m: String) = form { f ->
        if (!RestricoesSaude.ligaMarcadores(f.tipo)) f else f.copy(marcadores = if (m in f.marcadores) f.marcadores - m else f.marcadores + m, marcadoresEscolhidos = true)
    }
    private fun form(bloco: (FormularioItemSaude) -> FormularioItemSaude) = atual.update { e -> e.formulario?.let { e.copy(formulario = bloco(it), erroFormulario = null) } ?: e }

    fun salvar() {
        val e = atual.value; val f = e.formulario ?: return; val p = e.paciente ?: return
        if (e.enviando) return
        RestricoesSaude.validar(f, p.restricoesSaude.orEmpty().size)?.let { erro -> atual.update { it.copy(erroFormulario = erro) }; return }
        val lista = RestricoesSaude.salvarNaLista(p.restricoesSaude.orEmpty(), RestricoesSaude.item(f, novoId))
        gravar(p, lista, MensagemSaude.SALVO) { it.copy(formulario = null) }
    }

    fun pedirRemocao(id: Uuid) { if (podeEditar()) atual.update { it.copy(removendo = id, aviso = null) } }
    fun cancelarRemocao() = atual.update { it.copy(removendo = null) }
    fun confirmarRemocao() {
        val e = atual.value; val id = e.removendo ?: return; val p = e.paciente ?: return
        if (e.enviando) return
        gravar(p, RestricoesSaude.remover(p.restricoesSaude.orEmpty(), id), MensagemSaude.REMOVIDO) { it.copy(removendo = null, formulario = null) }
    }

    private fun podeEditar() = atual.value.paciente != null && !atual.value.encerrado && !atual.value.enviando

    private fun gravar(p: PacienteResposta, lista: List<RestricaoSaudeDto>, aviso: MensagemSaude, depois: (EstadoSaudePaciente) -> EstadoSaudePaciente) {
        viewModelScope.launch(dispatcher) {
            atual.update { it.copy(enviando = true, erro = null, aviso = null) }
            try {
                val salvo = pacientes.salvarRestricoes(p, lista)
                atual.update { depois(it.copy(paciente = if (it.paciente?.id == salvo.id) salvo else it.paciente, enviando = false, aviso = aviso)) }
            } catch (c: CancellationException) { throw c }
            catch (erro: ErroApi) {
                val msg = when (erro.codigo) {
                    "ITEM_INVALIDO" -> MensagemSaude.ITEM_INVALIDO; "ASSINATURA_INATIVA" -> MensagemSaude.ASSINATURA_INATIVA
                    "CONFLITO_VERSAO" -> MensagemSaude.CONFLITO; else -> MensagemSaude.ERRO
                }
                atual.update { it.copy(enviando = false, erro = msg) }
            } catch (_: FalhaComunicacao) { atual.update { it.copy(enviando = false, erro = MensagemSaude.SEM_CONEXAO) } }
            catch (_: SessaoAlterada) { atual.update { it.copy(enviando = false) } }
            catch (_: Exception) { atual.update { it.copy(enviando = false, erro = MensagemSaude.ERRO) } }
        }
    }
}
