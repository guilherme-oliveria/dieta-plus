package org.br.dietamais.ui.nutri.metas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.PortaMetasNutri
import org.br.dietamais.data.sync.novoIdLocal
import org.br.dietamais.dominio.metas.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

enum class MensagemMetas { SEM_CONEXAO, META_INVALIDA, CONFLITO, ASSINATURA_INATIVA, ERRO, META_SALVA, META_ENCERRADA, MODELO_APLICADO, MODELO_SALVO, MODELO_EXCLUIDO }

data class EstadoMetasNutri(val pacienteId: Uuid? = null, val carregando: Boolean = false, val carregado: Boolean = false,
    val metas: List<MetaResposta> = emptyList(), val cumprimento: CumprimentoMetasResposta? = null,
    val formulario: FormularioMeta? = null, val errosFormulario: Set<ErroMeta> = emptySet(),
    val modelosAbertos: Boolean = false, val modelos: List<ModeloMetaResposta>? = null, val nomeNovoModelo: String? = null,
    val enviando: Boolean = false, val semConexao: Boolean = false, val erro: MensagemMetas? = null, val aviso: MensagemMetas? = null,
    val hoje: LocalDate? = null) {
    /** Metas em vigor hoje; sem a data, mostra todas. */
    fun ativas() = metasAtivas(metas, hoje ?: LocalDate(1970, 1, 1))
    fun cumprimentoDe(metaId: Uuid) = cumprimento?.metas?.firstOrNull { it.metaId == metaId }
}

/** MOB-91 (H-04): metas do paciente, cumprimento da semana e modelos de metas. Tudo online (C-09); o backend valida (RN-MET-01 a 05). */
class MetasNutriViewModel(private val porta: PortaMetasNutri, private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    conectividade: Flow<Boolean>? = null, private val hoje: () -> LocalDate = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }) : ViewModel() {
    private val atual = MutableStateFlow(EstadoMetasNutri())
    val estado = atual.asStateFlow()

    init {
        conectividade?.let { fluxo -> viewModelScope.launch(dispatcher) {
            fluxo.collect { online ->
                val antes = atual.value
                atual.update { it.copy(semConexao = !online) }
                if (online && antes.semConexao && !antes.carregado && antes.pacienteId != null) recarregar()
            }
        } }
    }

    fun abrir(pacienteId: Uuid) {
        if (atual.value.pacienteId == pacienteId && (atual.value.carregado || atual.value.carregando)) return
        atual.update { EstadoMetasNutri(pacienteId = pacienteId, semConexao = it.semConexao, hoje = hoje()) }
        recarregar()
    }

    fun recarregar() {
        val id = atual.value.pacienteId ?: return
        executar(carregando = true) { carregar(id) }
    }

    private suspend fun carregar(id: Uuid) {
        val metas = porta.metas(id); val cumprimento = porta.cumprimento(id)
        atual.update { if (it.pacienteId == id) it.copy(metas = metas, cumprimento = cumprimento, carregado = true) else it }
    }

    // ---- formulário
    fun novaMeta() = atual.update { it.copy(formulario = novoFormularioMeta(hoje()), errosFormulario = emptySet(), aviso = null, erro = null) }
    fun editar(metaId: Uuid) {
        val meta = atual.value.metas.firstOrNull { it.id == metaId } ?: return
        atual.update { it.copy(formulario = formularioDe(meta), errosFormulario = emptySet(), aviso = null, erro = null) }
    }
    fun fecharFormulario() = atual.update { it.copy(formulario = null, errosFormulario = emptySet()) }
    fun mudarTipo(tipo: TipoMeta) = formulario { if (it.edicao || tipo !in TIPOS_CRIAVEIS) it else it.copy(tipo = tipo) }
    fun mudarNome(valor: String) = formulario { it.copy(nome = valor.take(LIMITE_NOME_META)) }
    fun mudarAlvo(valor: String) = formulario { it.copy(alvo = valor.take(12)) }
    fun mudarUnidade(valor: String) = formulario { it.copy(unidade = valor.take(LIMITE_UNIDADE_META)) }
    fun mudarFrequencia(valor: FrequenciaMeta) = formulario { if (valor == FrequenciaMeta.OUTRO) it else it.copy(frequencia = valor) }
    fun mudarVezes(delta: Int) = formulario { it.copy(vezesPorSemana = (it.vezesPorSemana + delta).coerceIn(1, 7)) }
    fun mudarInicio(valor: String) = formulario { it.copy(inicio = valor.take(10)) }
    fun mudarFim(valor: String) = formulario { it.copy(fim = valor.take(10)) }
    private fun formulario(bloco: (FormularioMeta) -> FormularioMeta) =
        atual.update { e -> e.formulario?.let { e.copy(formulario = bloco(it), errosFormulario = emptySet()) } ?: e }

    fun salvarMeta() {
        val e = atual.value; val f = e.formulario ?: return; val paciente = e.pacienteId ?: return
        if (e.enviando) return
        val erros = if (f.tipo == TipoMeta.AGUA) validarMeta(f).intersect(setOf(ErroMeta.ALVO)) else validarMeta(f)
        if (erros.isNotEmpty()) { atual.update { it.copy(errosFormulario = erros) }; return }
        executar {
            val id = f.metaId; val versao = f.versao
            if (id != null && versao != null) porta.atualizar(id, versao, atualizacaoMeta(f)) else porta.criar(paciente, requisicaoMeta(f, novoIdLocal()))
            atual.update { it.copy(formulario = null, aviso = MensagemMetas.META_SALVA) }
            carregar(paciente)
        }
    }

    /** A água não se encerra (RN-MET-05): o botão nem aparece nela. */
    fun encerrarMeta() {
        val e = atual.value; val f = e.formulario ?: return; val paciente = e.pacienteId ?: return
        val id = f.metaId ?: return; val versao = f.versao ?: return
        if (f.tipo == TipoMeta.AGUA || e.enviando) return
        executar {
            porta.atualizar(id, versao, AtualizarMetaRequisicao(encerrar = true))
            atual.update { it.copy(formulario = null, aviso = MensagemMetas.META_ENCERRADA) }
            carregar(paciente)
        }
    }

    // ---- modelos
    fun abrirModelos() {
        atual.update { it.copy(modelosAbertos = true, nomeNovoModelo = null, aviso = null, erro = null) }
        executar { val m = porta.modelos(); atual.update { it.copy(modelos = m) } }
    }
    fun fecharModelos() = atual.update { it.copy(modelosAbertos = false, nomeNovoModelo = null) }
    fun aplicarModelo(modeloId: Uuid) {
        val paciente = atual.value.pacienteId ?: return
        if (atual.value.enviando) return
        executar {
            porta.aplicarModelo(paciente, modeloId)
            atual.update { it.copy(modelosAbertos = false, aviso = MensagemMetas.MODELO_APLICADO) }
            carregar(paciente)
        }
    }
    /** "Salvar como modelo": copia as metas em vigor do paciente (sem a água). */
    fun iniciarNovoModelo() = atual.update { it.copy(nomeNovoModelo = "") }
    fun mudarNomeModelo(valor: String) = atual.update { e -> e.nomeNovoModelo?.let { e.copy(nomeNovoModelo = valor.take(LIMITE_NOME_META)) } ?: e }
    fun salvarModelo() {
        val e = atual.value; val nome = e.nomeNovoModelo?.trim() ?: return
        val itens = itensDeModelo(e.ativas())
        if (nome.isEmpty() || itens.isEmpty() || e.enviando) return
        executar {
            val criado = porta.criarModelo(ModeloMetaRequisicao(nome, itens))
            atual.update { it.copy(nomeNovoModelo = null, modelos = listOf(criado) + it.modelos.orEmpty(), aviso = MensagemMetas.MODELO_SALVO) }
        }
    }
    fun excluirModelo(id: Uuid) {
        val modelo = atual.value.modelos?.firstOrNull { it.id == id } ?: return
        if (atual.value.enviando) return
        executar {
            porta.excluirModelo(id, modelo.versao)
            atual.update { it.copy(modelos = it.modelos?.filterNot { m -> m.id == id }, aviso = MensagemMetas.MODELO_EXCLUIDO) }
        }
    }

    private fun executar(carregando: Boolean = false, bloco: suspend () -> Unit) {
        if (atual.value.semConexao) { atual.update { it.copy(erro = MensagemMetas.SEM_CONEXAO) }; return }
        atual.update { if (carregando) it.copy(carregando = true, erro = null) else it.copy(enviando = true, erro = null, aviso = null) }
        viewModelScope.launch(dispatcher) {
            val m = try { bloco(); null }
            catch (c: CancellationException) { throw c }
            catch (erro: ErroApi) { when (erro.codigo) {
                "META_INVALIDA" -> MensagemMetas.META_INVALIDA
                "CONFLITO_VERSAO" -> MensagemMetas.CONFLITO
                "ASSINATURA_INATIVA" -> MensagemMetas.ASSINATURA_INATIVA
                else -> MensagemMetas.ERRO
            } }
            catch (_: FalhaComunicacao) { MensagemMetas.SEM_CONEXAO }
            catch (_: SessaoAlterada) { null }
            catch (_: Exception) { MensagemMetas.ERRO }
            atual.update { it.copy(carregando = false, enviando = false, erro = m) }
            // conflito: recarrega e o formulário aberto passa a apontar para a versão nova, com o que a nutri digitou
            if (m == MensagemMetas.CONFLITO) atual.value.pacienteId?.let { id ->
                try { carregar(id) } catch (c: CancellationException) { throw c } catch (_: Exception) { return@launch }
                atual.update { e ->
                    val f = e.formulario; val nova = f?.metaId?.let { mid -> e.metas.firstOrNull { it.id == mid } }
                    if (f != null && nova != null) e.copy(formulario = f.copy(versao = nova.versao)) else e
                }
            }
        }
    }
}
