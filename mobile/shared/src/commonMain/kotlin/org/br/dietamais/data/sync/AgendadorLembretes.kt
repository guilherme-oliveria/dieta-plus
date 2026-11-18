package org.br.dietamais.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.data.repositorio.*
import org.br.dietamais.dominio.metas.planejarLembretesMeta
import org.br.dietamais.dominio.prescricoes.juntarLembretes
import org.br.dietamais.dominio.prescricoes.planejarLembretesSuplemento
import org.br.dietamais.dominio.rotina.*
import org.br.dietamais.plataforma.NotificacaoLocal
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Reagenda todos os lembretes locais sempre que a dieta em uso, os check-ins, os suplementos, as metas ou as preferências mudam (RN-ROT-05).
 * Sem permissão ou com o interruptor desligado, cancela tudo.
 */
class AgendadorLembretes(private val dietas: PortaDietaPaciente, private val rotina: PortaRotina, private val preferencias: PortaPreferencias,
    private val notificacao: NotificacaoLocal, private val textos: suspend () -> TextosLembrete,
    private val relogio: Clock = Clock.System, private val fuso: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val tique: Flow<Unit> = flow { while (true) { emit(Unit); kotlinx.coroutines.delay(6.hours) } },
    /** MOB-93: suplementos vigentes e os que o paciente desligou; nulo (web, testes antigos) não agenda suplemento. */
    private val prescricoes: PortaPrescricoesPaciente? = null,
    /** MOB-90b: metas com lembrete ligado neste aparelho; nulo (web, testes antigos) não agenda meta. */
    private val metas: PortaMetasPaciente? = null) {

    suspend fun executar() {
        val suplementos = prescricoes?.let { p -> combine(p.observarSuplementos(), p.observarSemLembrete()) { s, d -> s to d } }
            ?: flowOf(emptyList<org.br.dietamais.data.remoto.dto.SuplementoResposta>() to emptySet())
        val deMetas = metas?.let { p -> combine(p.observar(), p.observarLembretes()) { d, h -> d to h } }
            ?: flowOf(DadosMetasPaciente() to emptyMap<kotlin.uuid.Uuid, List<kotlinx.datetime.LocalTime>>())
        combine(dietas.observar(), rotina.observar(), preferencias.observarLembretes(), preferencias.observar().onStart { emit(null) }, tique) { d, r, ligado, prefs, _ ->
            Entrada(d, r, ligado, prefs?.silencio?.let { JanelaSilencio(it.ativo, it.inicio, it.fim) } ?: JanelaSilencio())
        }.combine(suplementos) { e, s -> e to s }.combine(deMetas) { (e, s), m -> Triple(e, s, m) }.collectLatest { (e, sup, met) ->
            try {
                val dieta = dietaVigente(e.dietas.snapshots, e.dietas.emUso?.dietaId)
                if ((dieta == null && sup.first.isEmpty() && met.second.isEmpty()) || !e.ligado || !notificacao.permitido()) { notificacao.cancelarTudo(); return@collectLatest }
                val agora = relogio.now(); val zona = fuso()
                val hoje = agora.toLocalDateTime(zona).date
                val refeicoes = if (dieta == null) emptyList() else {
                    val feitas = e.rotina.checkins.filter { it.data == hoje && it.dietaId == dieta.dietaId }.map { it.refeicaoId }.toSet()
                    planejarLembretes(dieta.refeicoes, agora, zona, e.silencio, feitas).map { it.copy(dietaId = dieta.dietaId) }
                }
                val t = textos()
                val deSuplemento = planejarLembretesSuplemento(sup.first, agora, zona, e.silencio, sup.second)
                    .map { it.copy(titulo = t.tituloSuplemento, corpo = t.corpoSuplemento) }
                val deMeta = planejarLembretesMeta(met.first.metas, met.first.registros, met.second, agora, zona, e.silencio)
                    .map { it.copy(titulo = t.tituloMeta, corpo = t.corpoMeta) }
                notificacao.agendar(juntarLembretes(refeicoes, deSuplemento + deMeta), t)
            } catch (c: CancellationException) { throw c }
            catch (_: Exception) { /* lembrete é melhor esforço: falha do agendador não derruba o app */ }
        }
    }

    private data class Entrada(val dietas: DadosDietaPaciente, val rotina: DadosRotina, val ligado: Boolean, val silencio: JanelaSilencio)
}
