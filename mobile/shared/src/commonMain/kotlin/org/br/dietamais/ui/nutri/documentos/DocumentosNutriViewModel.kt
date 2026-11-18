package org.br.dietamais.ui.nutri.documentos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.data.remoto.ErroApi
import org.br.dietamais.data.remoto.FalhaComunicacao
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.PortaConsultas
import org.br.dietamais.data.repositorio.PortaDocumentosNutri
import org.br.dietamais.data.repositorio.PortaPerfilNutri
import org.br.dietamais.data.sync.novoIdLocal
import org.br.dietamais.dominio.documentos.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

enum class MensagemDocumentos { SEM_CONEXAO, INVALIDO, JA_ANULADO, ASSINATURA_INATIVA, VINCULO_ENCERRADO, SO_LEITURA, CONFLITO, ERRO,
    EMITIDO, ANULADO, MODELO_SALVO, MODELO_EXCLUIDO }

internal fun mensagemDocumentos(falha: Exception): MensagemDocumentos = when (falha) {
    is FalhaComunicacao -> MensagemDocumentos.SEM_CONEXAO
    is ErroApi -> when (falha.codigo) {
        "DOCUMENTO_JA_ANULADO" -> MensagemDocumentos.JA_ANULADO
        "ASSINATURA_INATIVA" -> MensagemDocumentos.ASSINATURA_INATIVA
        "VINCULO_ENCERRADO" -> MensagemDocumentos.VINCULO_ENCERRADO
        "ACESSO_NEGADO" -> MensagemDocumentos.SO_LEITURA
        "CONFLITO_VERSAO" -> MensagemDocumentos.CONFLITO
        "FORMATO_INVALIDO", "OBRIGATORIO", "ITEM_INVALIDO" -> MensagemDocumentos.INVALIDO
        else -> MensagemDocumentos.ERRO
    }
    else -> MensagemDocumentos.ERRO
}

data class EstadoDocumentos(val pacienteId: Uuid? = null, val nomePaciente: String = "", val carregando: Boolean = false, val carregado: Boolean = false,
    val documentos: List<DocumentoResposta> = emptyList(), val modelos: List<ModeloDocumentoResposta>? = null, val consultas: List<ConsultaResposta> = emptyList(),
    val emissao: FormularioEmissao? = null, val errosEmissao: Set<ErroEmissao> = emptySet(), val nutri: String = "", val crn: String = "",
    val anulando: Uuid? = null, val motivo: String = "", val erroMotivo: Boolean = false,
    val enviando: Boolean = false, val semConexao: Boolean = false, val erro: MensagemDocumentos? = null, val aviso: MensagemDocumentos? = null,
    val hoje: LocalDate? = null) {
    fun consultasDoPaciente() = pacienteId?.let { consultasParaDeclaracao(consultas, it) }.orEmpty()
    fun modeloEscolhido() = emissao?.modeloId?.let { id -> modelos?.firstOrNull { it.id == id } }
}

/**
 * MOB-94 (H-06): documentos do paciente na ficha (lista, emitir, anular) e emissão da declaração a partir da consulta realizada.
 * Tudo online (BE-78); o PDF é aberto pelo link assinado. O backend preenche e decide.
 */
class DocumentosNutriViewModel(private val porta: PortaDocumentosNutri, private val consultas: PortaConsultas? = null,
    private val perfil: PortaPerfilNutri? = null, private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    conectividade: Flow<Boolean>? = null, private val fuso: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val hoje: () -> LocalDate = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }) : ViewModel() {
    private val atual = MutableStateFlow(EstadoDocumentos())
    val estado = atual.asStateFlow()
    private var carga: Job? = null

    init {
        conectividade?.let { fluxo -> viewModelScope.launch(dispatcher) {
            fluxo.collect { online ->
                val antes = atual.value
                atual.update { it.copy(semConexao = !online) }
                if (online && antes.semConexao && antes.pacienteId != null && !antes.carregado) recarregar()
            }
        } }
        consultas?.let { porta -> viewModelScope.launch(dispatcher) { porta.observar().catch { }.collect { c -> atual.update { it.copy(consultas = c) } } } }
    }

    fun abrir(pacienteId: Uuid, nome: String) {
        if (atual.value.pacienteId == pacienteId && atual.value.carregado) { atual.update { it.copy(nomePaciente = nome) }; return }
        atual.update { EstadoDocumentos(pacienteId = pacienteId, nomePaciente = nome, consultas = it.consultas, modelos = it.modelos, nutri = it.nutri, crn = it.crn,
            semConexao = it.semConexao) }
        recarregar()
    }

    fun recarregar() {
        val id = atual.value.pacienteId ?: return
        carga?.cancel()
        atual.update { it.copy(carregando = true, erro = null) }
        carga = viewModelScope.launch(dispatcher) {
            try {
                val docs = porta.documentos(id)
                if (atual.value.pacienteId == id) atual.update { it.copy(documentos = documentosOrdenados(docs), carregado = true, semConexao = false) }
            }
            catch (c: CancellationException) { throw c }
            catch (falha: Exception) { atual.update { it.copy(erro = mensagemDocumentos(falha)) } }
            finally { atual.update { it.copy(carregando = false) } }
        }
    }

    /** Abre o formulário de emissão. Com [consulta] (da agenda ou da ficha), já escolhe a declaração e a consulta. */
    fun abrirEmissao(consulta: ConsultaResposta? = null) {
        atual.update { it.copy(emissao = FormularioEmissao(consultaId = consulta?.id), errosEmissao = emptySet(), erro = null, aviso = null, hoje = hoje()) }
        viewModelScope.launch(dispatcher) {
            carregarModelos()
            atual.value.modelos?.let { lista ->
                val alvo = if (consulta != null) lista.firstOrNull { it.tipo == TipoDocumento.DECLARACAO_COMPARECIMENTO } else lista.firstOrNull()
                if (alvo != null && atual.value.emissao?.modeloId == null) escolherModelo(alvo.id)
            }
            carregarNutri()
        }
    }

    /** Agenda: declaração de uma consulta realizada sem passar pela ficha. */
    fun emitirDaConsulta(consulta: ConsultaResposta, nomePaciente: String) {
        if (atual.value.pacienteId != consulta.pacienteId) atual.update { EstadoDocumentos(pacienteId = consulta.pacienteId, nomePaciente = nomePaciente,
            consultas = it.consultas, modelos = it.modelos, nutri = it.nutri, crn = it.crn, semConexao = it.semConexao) }
        abrirEmissao(consulta)
    }

    fun escolherModelo(id: Uuid) = atual.update { e ->
        val m = e.modelos?.firstOrNull { it.id == id } ?: return@update e
        val f = e.emissao ?: return@update e
        val consulta = if (m.tipo == TipoDocumento.DECLARACAO_COMPARECIMENTO) f.consultaId ?: e.consultasDoPaciente().firstOrNull()?.id else null
        e.copy(emissao = f.copy(modeloId = m.id, tipo = m.tipo, consultaId = consulta), errosEmissao = emptySet())
    }
    fun escolherConsulta(id: Uuid) = emissao { it.copy(consultaId = id) }
    fun mudarData(texto: String) = emissao { it.copy(data = texto.take(10)) }
    fun mudarHorario(texto: String) = emissao { it.copy(horario = texto.take(5)) }
    fun fecharEmissao() { if (!atual.value.enviando) atual.update { it.copy(emissao = null, errosEmissao = emptySet(), erro = null) } }
    private fun emissao(muda: (FormularioEmissao) -> FormularioEmissao) = atual.update { e -> e.emissao?.let { e.copy(emissao = muda(it), errosEmissao = emptySet()) } ?: e }

    fun emitir() {
        val e = atual.value; val f = e.emissao ?: return; val paciente = e.pacienteId ?: return
        if (e.enviando) return
        val erros = validarEmissao(f)
        if (erros.isNotEmpty()) { atual.update { it.copy(errosEmissao = erros, erro = MensagemDocumentos.INVALIDO) }; return }
        val requisicao = requisicaoEmissao(f, novoIdLocal())
        atual.update { it.copy(enviando = true, erro = null, aviso = null) }
        viewModelScope.launch(dispatcher) {
            try {
                val doc = porta.emitir(paciente, requisicao)
                atual.update { it.copy(emissao = null, documentos = documentosOrdenados(listOf(doc) + it.documentos.filter { d -> d.id != doc.id }),
                    aviso = MensagemDocumentos.EMITIDO, semConexao = false) }
            }
            catch (c: CancellationException) { throw c }
            catch (falha: Exception) { atual.update { it.copy(erro = mensagemDocumentos(falha)) } }
            finally { atual.update { it.copy(enviando = false) } }
        }
    }

    fun pedirAnulacao(id: Uuid) = atual.update { it.copy(anulando = id, motivo = "", erroMotivo = false, erro = null, aviso = null) }
    fun mudarMotivo(texto: String) = atual.update { it.copy(motivo = texto.take(LIMITE_MOTIVO_ANULACAO), erroMotivo = false) }
    fun desistirDaAnulacao() { if (!atual.value.enviando) atual.update { it.copy(anulando = null, motivo = "", erroMotivo = false) } }

    fun anular() {
        val e = atual.value; val id = e.anulando ?: return
        if (e.enviando) return
        if (!motivoValido(e.motivo)) { atual.update { it.copy(erroMotivo = true) }; return }
        atual.update { it.copy(enviando = true, erro = null) }
        viewModelScope.launch(dispatcher) {
            try {
                val doc = porta.anular(id, e.motivo)
                atual.update { it.copy(anulando = null, motivo = "", documentos = it.documentos.map { d -> if (d.id == doc.id) doc else d }, aviso = MensagemDocumentos.ANULADO) }
            }
            catch (c: CancellationException) { throw c }
            catch (falha: Exception) {
                val m = mensagemDocumentos(falha)
                atual.update { it.copy(erro = m, anulando = if (m == MensagemDocumentos.JA_ANULADO) null else it.anulando) }
                if (m == MensagemDocumentos.JA_ANULADO) recarregar()
            }
            finally { atual.update { it.copy(enviando = false) } }
        }
    }

    /** Valores da prévia do documento que está sendo emitido. */
    fun previa(): List<TrechoPrevia>? {
        val e = atual.value; val f = e.emissao ?: return null; val m = e.modeloEscolhido() ?: return null
        val consulta = f.consultaId?.let { id -> e.consultas.firstOrNull { it.id == id } }?.takeIf { f.tipo == TipoDocumento.DECLARACAO_COMPARECIMENTO }
        return previaDocumento(m.texto, camposPrevia(f, e.nomePaciente, e.nutri, e.crn, consulta, e.hoje ?: hoje(), fuso()))
    }

    private suspend fun carregarModelos() {
        if (atual.value.modelos != null) return
        try { val m = porta.modelos(); atual.update { it.copy(modelos = modelosOrdenados(m)) } }
        catch (c: CancellationException) { throw c }
        catch (falha: Exception) { atual.update { it.copy(erro = mensagemDocumentos(falha)) } }
    }

    private suspend fun carregarNutri() {
        val p = perfil ?: return
        if (atual.value.nutri.isNotBlank()) return
        try { val r = p.obter(); atual.update { it.copy(nutri = r.nome, crn = r.crn) } }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { /* a prévia mostra "___"; o servidor preenche de verdade */ }
    }
}
