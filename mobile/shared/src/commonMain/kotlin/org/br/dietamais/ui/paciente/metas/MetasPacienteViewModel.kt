package org.br.dietamais.ui.paciente.metas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.data.remoto.dto.TipoMeta
import org.br.dietamais.data.repositorio.DadosMetasPaciente
import org.br.dietamais.data.repositorio.PortaMetasPaciente
import org.br.dietamais.data.repositorio.VALOR_MAXIMO_REGISTRO_META
import org.br.dietamais.dominio.metas.ProgressoMeta
import org.br.dietamais.dominio.metas.metaAguaAtual
import org.br.dietamais.dominio.metas.horariosPadraoMeta
import org.br.dietamais.dominio.metas.lerHorariosMeta
import org.br.dietamais.dominio.metas.textoHorariosMeta
import org.br.dietamais.dominio.metas.metaValeEm
import org.br.dietamais.dominio.metas.progressoMeta
import org.br.dietamais.dominio.metas.segundaDe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

enum class MensagemMetasPaciente { ERRO }

/**
 * MOB-90 (artboards `P-Hoje-Metas` e `P-Metas-Semana`). [hoje]: metas que valem hoje; [semana]: metas que valem em algum dia da semana.
 * A água fica fora daqui até a MOB-99: continua no cartão de água do Hoje.
 */
data class EstadoMetasPaciente(val carregado: Boolean = false, val hojeData: LocalDate? = null, val hoje: List<ProgressoMeta> = emptyList(),
    val semana: List<ProgressoMeta> = emptyList(), val verSemana: Boolean = false, val outroValor: Uuid? = null, val textoOutro: String = "",
    val erroOutro: Boolean = false, val erro: MensagemMetasPaciente? = null,
    /** MOB-90b: horários do lembrete local por meta (só neste aparelho) e a edição aberta deles. */
    val lembretes: Map<Uuid, List<LocalTime>> = emptyMap(), val editandoHorarios: Uuid? = null, val textoHorarios: String = "",
    val erroHorarios: Boolean = false)

class MetasPacienteViewModel(private val porta: PortaMetasPaciente, private val relogio: Clock = Clock.System,
    private val fuso: TimeZone = TimeZone.currentSystemDefault(), private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val tique: Flow<Unit> = flow { while (true) { emit(Unit); delay(1.minutes) } },
    private val aoGravar: () -> Unit = {}) : ViewModel() {
    private val atual = MutableStateFlow(EstadoMetasPaciente())
    val estado = atual.asStateFlow()
    private var dados = DadosMetasPaciente()
    private var observacao: Job? = null

    init {
        observar()
        viewModelScope.launch(dispatcher) { porta.falhas.collect { atual.update { it.copy(erro = MensagemMetasPaciente.ERRO) } } }
        viewModelScope.launch(dispatcher) { porta.observarLembretes().catch { }.collect { l -> atual.update { it.copy(lembretes = l) } } }
    }

    private fun observar() {
        observacao?.cancel()
        observacao = viewModelScope.launch(dispatcher) {
            combine(porta.observar().catch { atual.update { it.copy(erro = MensagemMetasPaciente.ERRO, carregado = true) } }, tique) { d, _ -> d }
                .collect { dados = it; recalcular() }
        }
    }

    fun tentarNovamente() { atual.update { it.copy(erro = null) }; observar() }

    private fun hojeLocal() = relogio.now().toLocalDateTime(fuso).date

    private fun recalcular() = atual.update { e ->
        val hoje = hojeLocal(); val segunda = segundaDe(hoje)
        val semana = (0..6).map { segunda.plus(DatePeriod(days = it)) }
        // MOB-99: a água entra na semana como as outras metas (uma só, a que o cartão de água usa); no Hoje ela fica no próprio cartão
        val agua = metaAguaAtual(dados.metas, hoje)
        val metas = (listOfNotNull(agua) + dados.metas.filter { it.tipo != TipoMeta.AGUA && it.tipo != TipoMeta.OUTRO }.sortedBy { it.criadoEm })
        val daSemana = metas.filter { m -> semana.any { metaValeEm(m, it) } }.map { progressoMeta(it, dados.registros, hoje) }
        e.copy(carregado = true, hojeData = hoje, semana = daSemana, hoje = daSemana.filter { it.meta.tipo != TipoMeta.AGUA && metaValeEm(it.meta, hoje) },
            outroValor = e.outroValor?.takeIf { id -> daSemana.any { it.meta.id == id } })
    }

    /** Soma [valor] na meta hoje (botões "+ 10 min", "+ 1 porção"...). */
    fun somar(metaId: Uuid, valor: Double) {
        if (valor <= 0 || valor > VALOR_MAXIMO_REGISTRO_META) return
        val p = atual.value.hoje.firstOrNull { it.meta.id == metaId } ?: return
        gravar(p.meta.id, valor)
    }

    /** SIM_NAO: "Cumpri hoje" grava 1 uma vez só no dia. */
    fun cumpriHoje(metaId: Uuid) {
        val p = atual.value.hoje.firstOrNull { it.meta.id == metaId && it.meta.tipo == TipoMeta.SIM_NAO } ?: return
        if (p.cumpridaHoje) return
        gravar(p.meta.id, 1.0)
    }

    fun abrirOutro(metaId: Uuid) = atual.update { it.copy(outroValor = metaId.takeIf { id -> id != it.outroValor }, textoOutro = "", erroOutro = false) }
    fun mudarTextoOutro(texto: String) = atual.update { it.copy(textoOutro = texto.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(9), erroOutro = false) }
    fun confirmarOutro() {
        val e = atual.value; val id = e.outroValor ?: return
        val valor = lerValorMeta(e.textoOutro)
        if (valor == null) { atual.update { it.copy(erroOutro = true) }; return }
        atual.update { it.copy(outroValor = null, textoOutro = "", erroOutro = false) }
        somar(id, valor)
    }

    fun abrirSemana() = atual.update { it.copy(verSemana = true) }
    fun fecharSemana() = atual.update { it.copy(verSemana = false, editandoHorarios = null, erroHorarios = false) }

    // ---- MOB-90b: lembrete local por meta
    /** Ligar usa os horários sugeridos do tipo; desligar apaga os horários deste aparelho. */
    fun alternarLembrete(metaId: Uuid, ligado: Boolean) {
        val p = atual.value.semana.firstOrNull { it.meta.id == metaId } ?: return
        definir(metaId, if (ligado) horariosPadraoMeta(p.meta.tipo) else emptyList())
        if (!ligado) atual.update { e -> if (e.editandoHorarios == metaId) e.copy(editandoHorarios = null, erroHorarios = false) else e }
    }
    fun editarHorarios(metaId: Uuid) = atual.update { e ->
        if (e.editandoHorarios == metaId) e.copy(editandoHorarios = null, erroHorarios = false)
        else e.copy(editandoHorarios = metaId, textoHorarios = textoHorariosMeta(e.lembretes[metaId].orEmpty()), erroHorarios = false)
    }
    fun mudarTextoHorarios(texto: String) = atual.update { it.copy(textoHorarios = texto.take(60), erroHorarios = false) }
    fun salvarHorarios() {
        val e = atual.value; val id = e.editandoHorarios ?: return
        val horarios = lerHorariosMeta(e.textoHorarios)
        if (horarios == null) { atual.update { it.copy(erroHorarios = true) }; return }
        definir(id, horarios)
        atual.update { it.copy(editandoHorarios = null, erroHorarios = false) }
    }
    private fun definir(metaId: Uuid, horarios: List<LocalTime>) {
        try { porta.definirLembrete(metaId, horarios) } catch (_: Exception) { atual.update { it.copy(erro = MensagemMetasPaciente.ERRO) } }
    }

    private fun gravar(metaId: Uuid, valor: Double) {
        try { porta.registrar(metaId, hojeLocal(), valor, relogio.now()); aoGravar() }
        catch (_: Exception) { atual.update { it.copy(erro = MensagemMetasPaciente.ERRO) } }
    }
}

/** "1,5" ou "1.5" para 1.5; fora de 0 < v ≤ 100.000 é inválido. */
fun lerValorMeta(texto: String): Double? = texto.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= VALOR_MAXIMO_REGISTRO_META && it.isFinite() }
