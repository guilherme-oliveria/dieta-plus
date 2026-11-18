package org.br.dietamais.ui.nutri.prescricoes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.PortaPrescricoesNutri
import org.br.dietamais.data.repositorio.PortaProdutos
import org.br.dietamais.data.sync.novoIdLocal
import org.br.dietamais.dominio.prescricoes.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

enum class SecaoPrescricoes { SUPLEMENTOS, ORIENTACOES }
enum class MensagemPrescricoes { SEM_CONEXAO, INVALIDO, CONFLITO, ASSINATURA_INATIVA, VINCULO_ENCERRADO, ERRO,
    SUPLEMENTO_SALVO, SUPLEMENTO_ENCERRADO, ORIENTACAO_ENVIADA, ORIENTACAO_ARQUIVADA, MODELO_EXCLUIDO }

data class EstadoPrescricoes(val pacienteId: Uuid? = null, val secao: SecaoPrescricoes = SecaoPrescricoes.SUPLEMENTOS, val hoje: LocalDate? = null,
    val carregando: Boolean = false, val carregado: Boolean = false,
    val suplementos: List<SuplementoResposta> = emptyList(), val orientacoes: List<OrientacaoResposta> = emptyList(),
    val suplemento: FormularioSuplemento? = null, val errosSuplemento: Set<ErroSuplemento> = emptySet(),
    val buscaProduto: String = "", val produtos: List<ProdutoResposta> = emptyList(), val mostrarEncerrados: Boolean = false,
    val orientacao: FormularioOrientacao? = null, val errosOrientacao: Set<ErroOrientacao> = emptySet(),
    val modelos: List<ModeloOrientacaoResposta>? = null, val escolhendoModelo: Boolean = false,
    val enviando: Boolean = false, val semConexao: Boolean = false, val erro: MensagemPrescricoes? = null, val aviso: MensagemPrescricoes? = null) {
    private val dia get() = hoje ?: LocalDate(1970, 1, 1)
    fun emUso() = suplementosEmUso(suplementos, dia)
    fun encerrados() = if (hoje == null) emptyList() else suplementosEncerrados(suplementos, dia)
}

/** MOB-92 (H-05): prescrever suplemento e enviar orientação, com modelos de orientação. Tudo online (C-09); o backend valida. */
class PrescricoesNutriViewModel(private val porta: PortaPrescricoesNutri, private val produtos: PortaProdutos? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate, conectividade: Flow<Boolean>? = null,
    private val hoje: () -> LocalDate = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
    private val frequenciaPadrao: String = "Todo dia") : ViewModel() {
    private val atual = MutableStateFlow(EstadoPrescricoes())
    val estado = atual.asStateFlow()
    private var busca: Job? = null

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
        atual.update { EstadoPrescricoes(pacienteId = pacienteId, semConexao = it.semConexao, hoje = hoje()) }
        recarregar()
    }
    fun recarregar() { val id = atual.value.pacienteId ?: return; executar(carregando = true) { carregar(id) } }
    private suspend fun carregar(id: Uuid) {
        val s = porta.suplementos(id); val o = porta.orientacoes(id)
        atual.update { if (it.pacienteId == id) it.copy(suplementos = s, orientacoes = orientacoesOrdenadas(o), carregado = true) else it }
    }
    fun mudarSecao(secao: SecaoPrescricoes) = atual.update { it.copy(secao = secao, aviso = null) }
    fun alternarEncerrados() = atual.update { it.copy(mostrarEncerrados = !it.mostrarEncerrados) }

    // ---- suplemento
    fun prescrever() = atual.update { it.copy(suplemento = novoFormularioSuplemento(hoje(), frequenciaPadrao), errosSuplemento = emptySet(),
        buscaProduto = "", produtos = emptyList(), aviso = null, erro = null) }
    fun editarSuplemento(id: Uuid) {
        val s = atual.value.suplementos.firstOrNull { it.id == id } ?: return
        atual.update { it.copy(suplemento = formularioDe(s), errosSuplemento = emptySet(), aviso = null, erro = null) }
    }
    fun fecharSuplemento() = atual.update { it.copy(suplemento = null, errosSuplemento = emptySet()) }
    fun mudarOrigem(o: OrigemSuplemento) = suplemento { if (it.edicao) it else it.copy(origem = o) }
    fun mudarNome(v: String) = suplemento { if (it.edicao) it else it.copy(nome = v.take(LIMITE_NOME_SUPLEMENTO)) }
    fun mudarDose(v: String) = suplemento { it.copy(dose = v.take(LIMITE_DOSE)) }
    fun mudarForma(v: String) = suplemento { it.copy(forma = if (it.forma == v) "" else v.take(LIMITE_FORMA)) }
    fun mudarFrequencia(v: String) = suplemento { it.copy(frequencia = v.take(LIMITE_FREQUENCIA)) }
    fun mudarHorarios(v: String) = suplemento { it.copy(horarios = v.take(60)) }
    fun mudarInicio(v: String) = suplemento { if (it.edicao) it else it.copy(inicio = v.take(10)) }
    fun mudarFim(v: String) = suplemento { it.copy(fim = v.take(10)) }
    private fun suplemento(bloco: (FormularioSuplemento) -> FormularioSuplemento) =
        atual.update { e -> e.suplemento?.let { e.copy(suplemento = bloco(it), errosSuplemento = emptySet()) } ?: e }

    /** Busca nos produtos da nutri (ativos); espera a digitação parar. */
    fun buscarProduto(texto: String) {
        atual.update { it.copy(buscaProduto = texto) }
        val porta = produtos ?: return
        busca?.cancel()
        if (texto.isBlank() || atual.value.semConexao) { atual.update { it.copy(produtos = emptyList()) }; return }
        busca = viewModelScope.launch(dispatcher) {
            delay(300)
            try { val r = porta.listar(texto.trim(), true, 0).itens; atual.update { if (it.buscaProduto == texto) it.copy(produtos = r) else it } }
            catch (c: CancellationException) { throw c } catch (_: Exception) { atual.update { it.copy(produtos = emptyList()) } }
        }
    }
    fun escolherProduto(p: ProdutoResposta) = suplemento { if (it.edicao) it else it.copy(produtoId = p.id, produtoNome = p.nome) }
        .also { atual.update { it.copy(produtos = emptyList(), buscaProduto = "") } }

    fun salvarSuplemento() {
        val e = atual.value; val f = e.suplemento ?: return; val paciente = e.pacienteId ?: return
        if (e.enviando) return
        val erros = validarSuplemento(f)
        if (erros.isNotEmpty()) { atual.update { it.copy(errosSuplemento = erros) }; return }
        executar {
            val id = f.suplementoId; val versao = f.versao
            if (id != null && versao != null) porta.atualizarSuplemento(id, versao, atualizacaoSuplemento(f))
            else porta.prescrever(paciente, requisicaoSuplemento(f, novoIdLocal()))
            atual.update { it.copy(suplemento = null, aviso = MensagemPrescricoes.SUPLEMENTO_SALVO) }
            carregar(paciente)
        }
    }
    fun encerrarSuplemento() {
        val e = atual.value; val f = e.suplemento ?: return; val paciente = e.pacienteId ?: return
        val id = f.suplementoId ?: return; val versao = f.versao ?: return
        if (e.enviando) return
        executar {
            porta.atualizarSuplemento(id, versao, AtualizarSuplementoRequisicao(encerrar = true))
            atual.update { it.copy(suplemento = null, aviso = MensagemPrescricoes.SUPLEMENTO_ENCERRADO) }
            carregar(paciente)
        }
    }

    // ---- orientação
    fun novaOrientacao() = atual.update { it.copy(orientacao = FormularioOrientacao(), errosOrientacao = emptySet(), escolhendoModelo = false, aviso = null, erro = null) }
    fun fecharOrientacao() = atual.update { it.copy(orientacao = null, errosOrientacao = emptySet(), escolhendoModelo = false) }
    fun mudarTitulo(v: String) = orientacao { it.copy(titulo = v.take(LIMITE_TITULO_ORIENTACAO)) }
    fun mudarTexto(v: String) = orientacao { it.copy(texto = v.take(LIMITE_TEXTO_ORIENTACAO)) }
    fun negrito() = orientacao { it.copy(texto = acrescentarNegrito(it.texto).take(LIMITE_TEXTO_ORIENTACAO)) }
    fun itemLista() = orientacao { it.copy(texto = acrescentarItemLista(it.texto).take(LIMITE_TEXTO_ORIENTACAO)) }
    fun alternarSalvarModelo() = orientacao { it.copy(salvarComoModelo = !it.salvarComoModelo) }
    private fun orientacao(bloco: (FormularioOrientacao) -> FormularioOrientacao) =
        atual.update { e -> e.orientacao?.let { e.copy(orientacao = bloco(it), errosOrientacao = emptySet()) } ?: e }

    fun usarModelo() {
        atual.update { it.copy(escolhendoModelo = true) }
        if (atual.value.modelos == null) executar { val m = porta.modelosOrientacao(); atual.update { it.copy(modelos = m) } }
    }
    fun fecharModelos() = atual.update { it.copy(escolhendoModelo = false) }
    fun escolherModelo(id: Uuid) {
        val m = atual.value.modelos?.firstOrNull { it.id == id } ?: return
        orientacao { it.copy(titulo = m.titulo, texto = m.texto, salvarComoModelo = false) }
        atual.update { it.copy(escolhendoModelo = false) }
    }
    fun excluirModelo(id: Uuid) {
        val m = atual.value.modelos?.firstOrNull { it.id == id } ?: return
        if (atual.value.enviando) return
        executar {
            porta.excluirModeloOrientacao(id, m.versao)
            atual.update { it.copy(modelos = it.modelos?.filterNot { x -> x.id == id }, aviso = MensagemPrescricoes.MODELO_EXCLUIDO) }
        }
    }

    /** Envia ao paciente (push genérico pelo servidor) e, se marcado, guarda também como modelo. */
    fun enviarOrientacao() {
        val e = atual.value; val f = e.orientacao ?: return; val paciente = e.pacienteId ?: return
        if (e.enviando) return
        val erros = validarOrientacao(f)
        if (erros.isNotEmpty()) { atual.update { it.copy(errosOrientacao = erros) }; return }
        executar {
            porta.enviarOrientacao(paciente, OrientacaoRequisicao(novoIdLocal(), f.titulo.trim(), f.texto.trim()))
            atual.update { it.copy(orientacao = null, aviso = MensagemPrescricoes.ORIENTACAO_ENVIADA) }
            if (f.salvarComoModelo) {
                val m = porta.criarModeloOrientacao(ModeloOrientacaoRequisicao(f.titulo.trim(), f.texto.trim()))
                atual.update { it.copy(modelos = it.modelos?.let { l -> listOf(m) + l }) }
            }
            carregar(paciente)
        }
    }
    fun arquivarOrientacao(id: Uuid) {
        val o = atual.value.orientacoes.firstOrNull { it.id == id } ?: return
        val paciente = atual.value.pacienteId ?: return
        if (atual.value.enviando || o.arquivadaEm != null) return
        executar {
            porta.atualizarOrientacao(id, o.versao, AtualizarOrientacaoRequisicao(arquivar = true))
            atual.update { it.copy(aviso = MensagemPrescricoes.ORIENTACAO_ARQUIVADA) }
            carregar(paciente)
        }
    }

    private fun executar(carregando: Boolean = false, bloco: suspend () -> Unit) {
        if (atual.value.semConexao) { atual.update { it.copy(erro = MensagemPrescricoes.SEM_CONEXAO) }; return }
        atual.update { if (carregando) it.copy(carregando = true, erro = null) else it.copy(enviando = true, erro = null, aviso = null) }
        viewModelScope.launch(dispatcher) {
            val m = try { bloco(); null }
            catch (c: CancellationException) { throw c }
            catch (erro: ErroApi) { when (erro.codigo) {
                "ITEM_INVALIDO", "FORMATO_INVALIDO", "OBRIGATORIO" -> MensagemPrescricoes.INVALIDO
                "CONFLITO_VERSAO" -> MensagemPrescricoes.CONFLITO
                "ASSINATURA_INATIVA" -> MensagemPrescricoes.ASSINATURA_INATIVA
                "ACESSO_NEGADO" -> MensagemPrescricoes.VINCULO_ENCERRADO
                else -> MensagemPrescricoes.ERRO
            } }
            catch (_: FalhaComunicacao) { MensagemPrescricoes.SEM_CONEXAO }
            catch (_: SessaoAlterada) { null }
            catch (_: Exception) { MensagemPrescricoes.ERRO }
            atual.update { it.copy(carregando = false, enviando = false, erro = m) }
            if (m == MensagemPrescricoes.CONFLITO) atual.value.pacienteId?.let { id ->
                try { carregar(id) } catch (c: CancellationException) { throw c } catch (_: Exception) { return@launch }
                atual.update { e ->
                    val f = e.suplemento; val novo = f?.suplementoId?.let { sid -> e.suplementos.firstOrNull { it.id == sid } }
                    if (f != null && novo != null) e.copy(suplemento = f.copy(versao = novo.versao)) else e
                }
            }
        }
    }
}
