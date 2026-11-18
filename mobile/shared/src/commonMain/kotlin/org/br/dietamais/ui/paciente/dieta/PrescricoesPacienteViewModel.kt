package org.br.dietamais.ui.paciente.dieta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.data.remoto.dto.OrientacaoResposta
import org.br.dietamais.data.remoto.dto.SuplementoResposta
import org.br.dietamais.data.repositorio.PortaPrescricoesPaciente
import org.br.dietamais.dominio.prescricoes.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class EstadoPrescricoesPaciente(val carregado: Boolean = false, val erro: Boolean = false,
    val suplementos: List<SuplementoResposta> = emptyList(), val proximas: Map<Uuid, Instant> = emptyMap(), val semLembrete: Set<Uuid> = emptySet(),
    val orientacoes: List<OrientacaoResposta> = emptyList(), val novas: Set<Uuid> = emptySet(),
    /** Selo "Nova" na tela aberta: as que eram novas quando o paciente abriu (abrir já as marca como vistas). */
    val destacadas: Set<Uuid> = emptySet(), val aberta: SecaoPrescricaoPaciente? = null, val orientacaoAberta: Uuid? = null,
    val agora: Instant? = null) {
    val temAlgo get() = suplementos.isNotEmpty() || orientacoes.isNotEmpty()
}

/** MOB-93 (H-05): Suplementos e Orientações do paciente, do banco local; lembrete por suplemento só no aparelho. */
class PrescricoesPacienteViewModel(private val porta: PortaPrescricoesPaciente, private val relogio: Clock = Clock.System,
    private val fuso: TimeZone = TimeZone.currentSystemDefault(), private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val tique: Flow<Unit> = flow { while (true) { emit(Unit); delay(1.minutes) } }) : ViewModel() {
    private val atual = MutableStateFlow(EstadoPrescricoesPaciente())
    val estado = atual.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            combine(porta.observarSuplementos(), porta.observarSemLembrete(), porta.observarOrientacoes(), porta.observarVistas(), tique) { s, sem, o, vistas, _ ->
                Leitura(s, sem, o, vistas)
            }.catch { atual.update { it.copy(carregado = true, erro = true) } }.collect { l ->
                val agora = relogio.now()
                val vigentes = suplementosVigentes(l.suplementos, agora.toLocalDateTime(fuso).date)
                val visiveis = orientacoesVisiveis(l.orientacoes)
                val novas = visiveis.map { it.id }.filter { it !in l.vistas }.toSet()
                atual.update { e ->
                    e.copy(carregado = true, erro = false, suplementos = vigentes, semLembrete = l.semLembrete, orientacoes = visiveis, novas = novas, agora = agora,
                        proximas = vigentes.mapNotNull { s -> proximaTomada(s, agora, fuso)?.let { s.id to it } }.toMap())
                }
                // com a tela de orientações aberta, a que chega pelo sync já conta como vista, mas mantém o selo
                if (atual.value.aberta == SecaoPrescricaoPaciente.ORIENTACOES && novas.isNotEmpty()) marcarVistas(novas)
            }
        }
    }

    fun abrir(secao: SecaoPrescricaoPaciente) {
        val e = atual.value
        atual.update { it.copy(aberta = secao, orientacaoAberta = null, destacadas = if (secao == SecaoPrescricaoPaciente.ORIENTACOES) it.novas else emptySet()) }
        if (secao == SecaoPrescricaoPaciente.ORIENTACOES) marcarVistas(e.novas)
    }

    fun fechar() = atual.update { it.copy(aberta = null, orientacaoAberta = null, destacadas = emptySet()) }

    /** Abre ou recolhe uma orientação anterior (a mais nova já aparece aberta). */
    fun alternarOrientacao(id: Uuid) = atual.update { it.copy(orientacaoAberta = if (it.orientacaoAberta == id) null else id) }

    fun mudarLembrete(suplemento: Uuid, ligado: Boolean) {
        atual.update { it.copy(semLembrete = if (ligado) it.semLembrete - suplemento else it.semLembrete + suplemento) }
        try { porta.definirLembrete(suplemento, ligado) } catch (_: Exception) {
            atual.update { it.copy(semLembrete = if (ligado) it.semLembrete + suplemento else it.semLembrete - suplemento) }
        }
    }

    private fun marcarVistas(ids: Set<Uuid>) {
        if (ids.isEmpty()) return
        atual.update { it.copy(destacadas = it.destacadas + ids) }
        try { porta.marcarVistas(ids) } catch (_: Exception) { /* o selo volta na próxima abertura; nada se perde */ }
    }

    private data class Leitura(val suplementos: List<SuplementoResposta>, val semLembrete: Set<Uuid>, val orientacoes: List<OrientacaoResposta>, val vistas: Set<Uuid>)
}
