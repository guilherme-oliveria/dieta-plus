package org.br.dietamais.ui.nutri.documentos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.br.dietamais.data.remoto.dto.ModeloDocumentoResposta
import org.br.dietamais.data.remoto.dto.TipoDocumento
import org.br.dietamais.data.repositorio.PortaDocumentosNutri
import org.br.dietamais.dominio.documentos.*
import kotlin.uuid.Uuid

data class EstadoModelosDocumento(val carregando: Boolean = false, val modelos: List<ModeloDocumentoResposta>? = null,
    val formulario: FormularioModeloDoc? = null, val erros: Set<ErroModeloDoc> = emptySet(), val excluindo: Uuid? = null,
    val enviando: Boolean = false, val semConexao: Boolean = false, val erro: MensagemDocumentos? = null, val aviso: MensagemDocumentos? = null)

/** MOB-94 (RN-DOC-01, artboard `A-Modelos-Documento`): na Conta, os modelos do Dieta+ (copiar para editar) e os da nutri (editar, apagar). Online. */
class ModelosDocumentoViewModel(private val porta: PortaDocumentosNutri, private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    conectividade: Flow<Boolean>? = null, private val sufixoCopia: String = "(cópia)") : ViewModel() {
    private val atual = MutableStateFlow(EstadoModelosDocumento())
    val estado = atual.asStateFlow()

    init {
        conectividade?.let { fluxo -> viewModelScope.launch(dispatcher) {
            fluxo.collect { online ->
                val antes = atual.value
                atual.update { it.copy(semConexao = !online) }
                if (online && antes.semConexao && antes.modelos == null) recarregar()
            }
        } }
    }

    fun recarregar() {
        atual.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch(dispatcher) {
            try { val m = porta.modelos(); atual.update { it.copy(modelos = modelosOrdenados(m), semConexao = false) } }
            catch (c: CancellationException) { throw c }
            catch (falha: Exception) { atual.update { it.copy(erro = mensagemDocumentos(falha)) } }
            finally { atual.update { it.copy(carregando = false) } }
        }
    }

    /** Modelo do sistema: abre uma cópia; modelo dela: edita. */
    fun abrir(id: Uuid) = atual.update { e ->
        val m = e.modelos?.firstOrNull { it.id == id } ?: return@update e
        e.copy(formulario = formularioDeModelo(m, sufixoCopia), erros = emptySet(), erro = null, aviso = null)
    }
    fun novo() = atual.update { it.copy(formulario = FormularioModeloDoc(), erros = emptySet(), erro = null, aviso = null) }
    fun mudarTipo(tipo: TipoDocumento) = formulario { it.copy(tipo = tipo) }
    fun mudarNome(texto: String) = formulario { it.copy(nome = texto.take(LIMITE_NOME_MODELO_DOC)) }
    fun mudarTexto(texto: String) = formulario { it.copy(texto = texto.take(LIMITE_TEXTO_DOC)) }
    /** Acrescenta um campo de preenchimento (`{{paciente}}`...) no fim do texto. */
    fun inserirCampo(campo: String) = formulario { it.copy(texto = (it.texto + "{{$campo}}").take(LIMITE_TEXTO_DOC)) }
    fun fechar() { if (!atual.value.enviando) atual.update { it.copy(formulario = null, erros = emptySet(), erro = null) } }
    private fun formulario(muda: (FormularioModeloDoc) -> FormularioModeloDoc) = atual.update { e -> e.formulario?.let { e.copy(formulario = muda(it), erros = emptySet()) } ?: e }

    fun salvar() {
        val e = atual.value; val f = e.formulario ?: return
        if (e.enviando) return
        val erros = validarModeloDoc(f)
        if (erros.isNotEmpty()) { atual.update { it.copy(erros = erros, erro = MensagemDocumentos.INVALIDO) }; return }
        atual.update { it.copy(enviando = true, erro = null, aviso = null) }
        viewModelScope.launch(dispatcher) {
            try {
                val r = if (f.modeloId != null) porta.atualizarModelo(f.modeloId, f.versao ?: 0, requisicaoModeloDoc(f)) else porta.criarModelo(requisicaoModeloDoc(f))
                atual.update { it.copy(formulario = null, modelos = modelosOrdenados(it.modelos.orEmpty().filter { m -> m.id != r.id } + r), aviso = MensagemDocumentos.MODELO_SALVO) }
            }
            catch (c: CancellationException) { throw c }
            catch (falha: Exception) {
                val m = mensagemDocumentos(falha)
                atual.update { it.copy(erro = m) }
                if (m == MensagemDocumentos.CONFLITO) recarregarEReapontar()
            }
            finally { atual.update { it.copy(enviando = false) } }
        }
    }

    fun pedirExclusao(id: Uuid) = atual.update { it.copy(excluindo = id, erro = null, aviso = null) }
    fun desistirDaExclusao() { if (!atual.value.enviando) atual.update { it.copy(excluindo = null) } }

    fun excluir() {
        val e = atual.value; val id = e.excluindo ?: return
        val m = e.modelos?.firstOrNull { it.id == id } ?: return
        if (e.enviando || m.doSistema) return
        atual.update { it.copy(enviando = true, erro = null) }
        viewModelScope.launch(dispatcher) {
            try {
                porta.excluirModelo(id, m.versao)
                atual.update { it.copy(excluindo = null, modelos = it.modelos?.filter { x -> x.id != id }, aviso = MensagemDocumentos.MODELO_EXCLUIDO) }
            }
            catch (c: CancellationException) { throw c }
            catch (falha: Exception) { atual.update { it.copy(erro = mensagemDocumentos(falha), excluindo = null) }; recarregar() }
            finally { atual.update { it.copy(enviando = false) } }
        }
    }

    /** Conflito ao salvar: traz a versão nova e mantém o texto da nutri no formulário, agora sobre a versão atual. */
    private suspend fun recarregarEReapontar() {
        try {
            val lista = modelosOrdenados(porta.modelos())
            atual.update { e -> e.copy(modelos = lista, formulario = e.formulario?.let { f -> f.modeloId?.let { id -> lista.firstOrNull { it.id == id } }?.let { f.copy(versao = it.versao) } ?: f }) }
        }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { }
    }
}
