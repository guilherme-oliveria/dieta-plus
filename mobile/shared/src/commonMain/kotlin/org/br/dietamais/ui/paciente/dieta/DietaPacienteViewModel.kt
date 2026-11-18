package org.br.dietamais.ui.paciente.dieta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.StatusCheckin
import org.br.dietamais.data.remoto.dto.TrocaCheckin
import org.br.dietamais.data.repositorio.*
import org.br.dietamais.dominio.rotina.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/** Hoje, Dieta, check-in, troca e água do paciente (MOB-14, 15, 17). Leitura e escrita são locais; só a troca de dieta em uso exige rede. */
class DietaPacienteViewModel(private val repositorio: PortaDietaPaciente, private val rotina: PortaRotina,
    private val relogio: Clock = Clock.System, private val fuso: TimeZone = TimeZone.currentSystemDefault(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate, conectividade: Flow<Boolean>? = null,
    private val tique: Flow<Unit> = flow { while (true) { emit(Unit); delay(1.minutes) } },
    private val aoGravar: () -> Unit = {}, private val prazoDesfazerMs: Long = PRAZO_DESFAZER_MS,
    /** MOB-88: preferência do aparelho; ligada, "Comi" abre a pergunta de fome e sensação antes do prazo de desfazer. */
    perguntarHumor: Flow<Boolean> = flowOf(false)) : ViewModel() {
    private val atual = MutableStateFlow(EstadoDietaPaciente())
    val estado = atual.asStateFlow()
    private var dados = DadosDietaPaciente()
    private var registros = DadosRotina()
    private var diaEscolhido: LocalDate? = null
    private var observacao: Job? = null
    private var pendente: CheckinPendente? = null
    private var prazoDesfazer: Job? = null
    private val aguaPendente = mutableListOf<Pair<AguaPendente, Job?>>()
    private var perguntar = false
    private var foraPendente: ForaPendente? = null
    private var prazoFora: Job? = null

    init {
        observarDados()
        viewModelScope.launch(dispatcher) { rotina.falhas.collect { atual.update { it.copy(erro = MensagemDietaPaciente.ERRO) } } }
        conectividade?.let { fluxo -> viewModelScope.launch(dispatcher) { fluxo.collect { online -> atual.update { it.copy(semConexao = !online) } } } }
        viewModelScope.launch(dispatcher) { perguntarHumor.catch { }.collect { perguntar = it } }
    }

    private fun observarDados() {
        observacao?.cancel()
        observacao = viewModelScope.launch(dispatcher) {
            combine(repositorio.observar().catch { atual.update { it.copy(erro = MensagemDietaPaciente.ERRO, carregado = true) } },
                rotina.observar().catch { atual.update { it.copy(erro = MensagemDietaPaciente.ERRO, carregado = true) } }, tique) { d, r, _ -> d to r }
                .collect { (d, r) -> dados = d; registros = r; recalcular() }
        }
    }

    fun tentarNovamente() { atual.update { it.copy(erro = null) }; observarDados() }

    private fun agoraLocal() = relogio.now().toLocalDateTime(fuso)

    private fun recalcular() = atual.update { e ->
        val agora = agoraLocal(); val hoje = agora.date; val dias = diasComCheckin(hoje)
        val dia = diaEscolhido?.takeIf { it in dias } ?: hoje
        val dieta = dietaVigente(dados.snapshots, dados.emUso?.dietaId)
        val porNutri = dados.snapshots.sortedByDescending { it.publicadoEm }.distinctBy { it.nutricionista.id }
        // RN-ROT-01: um check-in por refeição/dia, o último vale
        val gravados = registros.checkins.filter { it.data == dia && it.dietaId == dieta?.dietaId }.groupBy { it.refeicaoId }
            .mapValues { (_, lista) -> lista.maxBy { it.registradoEm }.status }
        // MOB-UX-04: o check-in que ainda espera o prazo de desfazer já aparece como registrado
        val p = pendente?.takeIf { it.dia == dia && it.dietaId == dieta?.dietaId }
        val doDia = if (p == null) gravados else gravados + (p.refeicaoId to p.status)
        val peso = registros.pesos.maxByOrNull { it.registradoEm }?.pesoKg
        val aguaGravada = registros.agua.filter { it.registradoEm.toLocalDateTime(fuso).date == dia }.sumOf { it.ml }
        val aguaDoDia = totalAguaMl(aguaGravada, aguaPendente.map { it.first }.filter { it.registradoEm.toLocalDateTime(fuso).date == dia })
        val mudou = dieta?.id != e.dieta?.id
        val grupos = dieta?.let { gruposListaPaciente(it.listaSubstituicao, it.refeicoes) }.orEmpty()
        e.copy(carregado = true, dieta = dieta, refeicoes = dieta?.let { refeicoesOrdenadas(it.refeicoes) }.orEmpty(),
            proxima = if (dia == hoje) dieta?.let { proximaRefeicao(it.refeicoes.filter { r -> r.id !in doDia }, agora.time) } else null,
            alternativas = if (porNutri.size > 1) porNutri else emptyList(), versaoSelecao = dados.emUso?.versao ?: 0,
            hoje = hoje, dia = dia, dias = dias, checkins = doDia, podeDesfazer = pendente != null, podeDesfazerAgua = aguaPendente.isNotEmpty(), aguaMl = aguaDoDia, metaAguaMl = metaAguaMl(peso),
            gruposLista = grupos, buscaLista = if (mudou) "" else e.buscaLista,
            grupoListaFiltro = e.grupoListaFiltro.takeIf { !mudou && grupos.any { g -> g.id == it } },
            grupoListaAberto = if (mudou) grupos.firstOrNull()?.id else e.grupoListaAberto.takeIf { id -> grupos.any { it.id == id } },
            troca = e.troca.takeUnless { mudou })
    }

    fun abrirRefeicao(id: Uuid?) = atual.update { it.copy(refeicaoAberta = id) }
    fun abrirLista() = atual.update { it.copy(listaAberta = true) }
    fun fecharLista() = atual.update { it.copy(listaAberta = false) }
    fun buscarNaLista(busca: String) = atual.update { e ->
        val termo = busca.take(200)
        val visiveis = filtrarListaPaciente(e.gruposLista, termo, e.grupoListaFiltro)
        e.copy(buscaLista = termo, grupoListaAberto = e.grupoListaAberto.takeIf { id -> visiveis.any { it.id == id } } ?: visiveis.firstOrNull()?.id)
    }
    fun filtrarGrupoLista(id: Uuid?) = atual.update { e ->
        if (id != null && e.gruposLista.none { it.id == id }) e else e.copy(grupoListaFiltro = id, grupoListaAberto = id ?: e.grupoListaAberto)
    }
    fun expandirGrupoLista(id: Uuid) = atual.update { e ->
        if (e.gruposLista.none { it.id == id }) e else e.copy(grupoListaAberto = if (e.grupoListaAberto == id) null else id)
    }

    fun mudarDia(dia: LocalDate) { diaEscolhido = dia; recalcular() }

    // ---- check-in (MOB-17)
    fun marcar(refeicaoId: Uuid, status: StatusCheckin) {
        val e = atual.value; val dieta = e.dieta ?: return; val dia = e.dia ?: return
        if (status == StatusCheckin.TROCADO || status == StatusCheckin.OUTRO || dieta.refeicoes.none { it.id == refeicaoId }) return
        // grava o anterior se houver; o novo espera o prazo, porque o contrato só inclui check-ins (RN-ROT-02) e não dá para remover um já enviado
        confirmarPendente()
        pendente = CheckinPendente(dieta.dietaId, refeicaoId, dia, status, relogio.now())
        recalcular()
        // MOB-88: com a pergunta ligada, o prazo de desfazer começa depois de Salvar ou Pular; desfazer apaga o registro inteiro
        if (status == StatusCheckin.FEITO && perguntar) {
            val nome = dieta.refeicoes.first { it.id == refeicaoId }.nome
            atual.update { it.copy(humor = EstadoHumor(refeicaoId, nome)) }
        } else iniciarPrazo()
    }

    private fun iniciarPrazo() {
        prazoDesfazer?.cancel()
        prazoDesfazer = viewModelScope.launch(dispatcher) { delay(prazoDesfazerMs); confirmarPendente(); recalcular() }
    }

    /** Toque no valor já escolhido limpa a escala (as duas perguntas são opcionais). */
    fun escolherFome(valor: Int) = atual.update { e -> e.humor?.let { h -> e.copy(humor = h.copy(fome = valor.takeIf { it != h.fome && valorEscala(it) != null })) } ?: e }
    fun escolherSensacao(valor: Int) = atual.update { e -> e.humor?.let { h -> e.copy(humor = h.copy(sensacao = valor.takeIf { it != h.sensacao && valorEscala(it) != null })) } ?: e }

    fun salvarHumor() {
        val h = atual.value.humor ?: return
        pendente = pendente?.takeIf { it.refeicaoId == h.refeicaoId }?.copy(fomeAntes = h.fome, sensacaoDepois = h.sensacao)
        atual.update { it.copy(humor = null) }
        if (pendente != null) iniciarPrazo()
    }

    fun pularHumor() {
        if (atual.value.humor == null) return
        atual.update { it.copy(humor = null) }
        if (pendente != null) iniciarPrazo()
    }

    /** MOB-UX-04: descarta o check-in que ainda não foi gravado; a refeição volta ao estado anterior. */
    fun desfazerCheckin() {
        if (pendente == null) return
        prazoDesfazer?.cancel(); prazoDesfazer = null; pendente = null
        atual.update { it.copy(humor = null) }
        recalcular()
    }

    private fun confirmarPendente() {
        val p = pendente ?: return
        prazoDesfazer?.cancel(); prazoDesfazer = null; pendente = null
        if (atual.value.humor?.refeicaoId == p.refeicaoId) atual.update { it.copy(humor = null) }
        gravar {
            if (p.fomeAntes == null && p.sensacaoDepois == null) rotina.registrarCheckin(p.dietaId, p.refeicaoId, p.dia, p.status, emptyList(), p.registradoEm)
            else rotina.registrarCheckin(p.dietaId, p.refeicaoId, p.dia, p.status, emptyList(), p.registradoEm, p.fomeAntes, p.sensacaoDepois)
        }
    }

    // ---- fora do plano (MOB-88, RN-ROT-06)
    fun abrirForaDoPlano() {
        val agora = agoraLocal().time
        atual.update { it.copy(foraDoPlano = FormularioForaDoPlano(hora = "${agora.hour.toString().padStart(2, '0')}:${agora.minute.toString().padStart(2, '0')}"), errosForaDoPlano = emptySet()) }
    }
    fun fecharForaDoPlano() = atual.update { it.copy(foraDoPlano = null, errosForaDoPlano = emptySet()) }
    fun mudarTextoFora(valor: String) = formularioFora { it.copy(texto = valor.take(LIMITE_FORA_DO_PLANO)) }
    fun mudarRefeicaoFora(id: Uuid?) = formularioFora { it.copy(refeicaoId = id) }
    fun mudarHoraFora(valor: String) = formularioFora { it.copy(hora = valor.take(5)) }
    private fun formularioFora(bloco: (FormularioForaDoPlano) -> FormularioForaDoPlano) =
        atual.update { e -> e.foraDoPlano?.let { e.copy(foraDoPlano = bloco(it), errosForaDoPlano = emptySet()) } ?: e }

    /** Grava depois do prazo de desfazer, como o check-in: o contrato só inclui (RN-ROT-06) e não dá para remover um já enviado. */
    fun registrarForaDoPlano() {
        val e = atual.value; val f = e.foraDoPlano ?: return; val dia = e.dia ?: return
        val (erros, hora) = validarForaDoPlano(f)
        val quando = hora?.let { (h, m) -> LocalDateTime(dia, LocalTime(h, m)).toInstant(fuso) }
        val todos = if (quando != null && quando > relogio.now()) erros + ErroForaDoPlano.HORA else erros
        if (todos.isNotEmpty() || quando == null) { atual.update { it.copy(errosForaDoPlano = todos) }; return }
        confirmarFora()
        val refeicao = f.refeicaoId?.takeIf { id -> e.dieta?.refeicoes?.any { it.id == id } == true }
        foraPendente = ForaPendente(dia, f.texto.trim(), refeicao, quando)
        atual.update { it.copy(foraDoPlano = null, errosForaDoPlano = emptySet(), podeDesfazerFora = true) }
        prazoFora = viewModelScope.launch(dispatcher) { delay(prazoDesfazerMs); confirmarFora() }
    }

    fun desfazerForaDoPlano() {
        if (foraPendente == null) return
        prazoFora?.cancel(); prazoFora = null; foraPendente = null
        atual.update { it.copy(podeDesfazerFora = false) }
    }

    private fun confirmarFora() {
        val p = foraPendente ?: return
        prazoFora?.cancel(); prazoFora = null; foraPendente = null
        atual.update { it.copy(podeDesfazerFora = false) }
        gravar { rotina.registrarForaDoPlano(p.dia, p.texto, p.refeicaoId, p.registradoEm) }
    }

    override fun onCleared() { confirmarPendente(); confirmarFora(); aguaPendente.toList().forEach { confirmarAgua(it.first) }; super.onCleared() }

    /** MOB-UX-05: o lançamento só vai à fila depois do prazo, porque o contrato só inclui água (RN-ROT-03); até lá dá para desfazer. */
    fun beberAgua(ml: Int) {
        if (ml !in 1..5000) return
        val lancamento = AguaPendente(ml, relogio.now())
        val entrada = lancamento to null as Job?
        aguaPendente += entrada
        recalcular()
        val job = viewModelScope.launch(dispatcher) { delay(prazoDesfazerMs); confirmarAgua(lancamento); recalcular() }
        val i = aguaPendente.indexOfFirst { it.first === lancamento }
        if (i >= 0) aguaPendente[i] = lancamento to job
    }

    /** Descarta o último lançamento de água ainda não enviado. */
    fun desfazerAgua() {
        val ultimo = aguaPendente.removeLastOrNull() ?: return
        ultimo.second?.cancel()
        recalcular()
    }

    private fun confirmarAgua(l: AguaPendente) {
        val i = aguaPendente.indexOfFirst { it.first === l }
        if (i < 0) return
        aguaPendente.removeAt(i)
        gravar { rotina.registrarAgua(l.ml, l.registradoEm) }
    }

    // ---- troca de alimento (MOB-15)
    fun abrirTroca(refeicaoId: Uuid) = abrirTroca(refeicaoId, null)
    fun abrirTroca(refeicaoId: Uuid, itemId: Uuid?) = atual.update { e ->
        val dieta = e.dieta ?: return@update e
        val refeicao = dieta.refeicoes.firstOrNull { it.id == refeicaoId } ?: return@update e
        if (itemId != null && refeicao.itens.none { it.id == itemId && substitutosDoItem(it, dieta.listaSubstituicao).isNotEmpty() }) e
        else e.copy(troca = EstadoTroca(refeicaoId, itemId = itemId))
    }
    fun cancelarTroca() = atual.update { it.copy(troca = null) }
    fun escolherSubstituto(itemId: Uuid, alimentoId: Uuid?) = atual.update { e ->
        val t = e.troca ?: return@update e
        if (t.itemId != null && t.itemId != itemId) return@update e
        e.copy(troca = t.copy(escolhas = if (alimentoId == null) t.escolhas - itemId else t.escolhas + (itemId to alimentoId)))
    }
    fun confirmarTroca() {
        val e = atual.value; val t = e.troca ?: return; val dieta = e.dieta ?: return; val dia = e.dia ?: return
        val refeicao = dieta.refeicoes.firstOrNull { it.id == t.refeicaoId } ?: return
        // só vale item da refeição com substituto permitido pela lista congelada (o backend confere de novo)
        val trocas = t.escolhas.mapNotNull { (itemId, alimentoId) ->
            val item = refeicao.itens.firstOrNull { it.id == itemId } ?: return@mapNotNull null
            if (substitutosDoItem(item, dieta.listaSubstituicao).none { it.alimento.id == alimentoId }) null else TrocaCheckin(itemId, alimentoId)
        }
        if (trocas.isEmpty()) return
        if (gravar { rotina.registrarCheckin(dieta.dietaId, t.refeicaoId, dia, StatusCheckin.TROCADO, trocas, relogio.now()) }) {
            atual.update { it.copy(troca = null) }
        }
    }

    private fun gravar(bloco: () -> Unit): Boolean {
        return try { bloco(); atual.update { it.copy(erro = null) }; aoGravar(); true }
        catch (c: CancellationException) { throw c }
        catch (_: SessaoAlterada) { false }
        catch (_: Exception) { atual.update { it.copy(erro = MensagemDietaPaciente.ERRO) }; false }
    }

    fun escolher(dietaId: Uuid) {
        val e = atual.value
        if (e.enviando) return
        if (e.semConexao) { atual.update { it.copy(erro = MensagemDietaPaciente.SEM_CONEXAO) }; return }
        viewModelScope.launch(dispatcher) {
            atual.update { it.copy(enviando = true, erro = null) }
            try {
                val resposta = repositorio.selecionar(dietaId, e.versaoSelecao)
                dados = dados.copy(emUso = resposta); recalcular()
                atual.update { it.copy(enviando = false) }
            } catch (c: CancellationException) { throw c }
            catch (erro: ErroApi) {
                atual.update { it.copy(enviando = false, erro = if (erro.codigo == "CONFLITO_VERSAO") MensagemDietaPaciente.CONFLITO else MensagemDietaPaciente.ERRO) }
            } catch (_: FalhaComunicacao) { atual.update { it.copy(enviando = false, erro = MensagemDietaPaciente.SEM_CONEXAO) } }
            catch (_: SessaoAlterada) { atual.update { it.copy(enviando = false) } }
            catch (_: Exception) { atual.update { it.copy(enviando = false, erro = MensagemDietaPaciente.ERRO) } }
        }
    }
}

private data class CheckinPendente(val dietaId: Uuid, val refeicaoId: Uuid, val dia: LocalDate, val status: StatusCheckin, val registradoEm: kotlin.time.Instant,
    val fomeAntes: Int? = null, val sensacaoDepois: Int? = null)

private class ForaPendente(val dia: LocalDate, val texto: String, val refeicaoId: Uuid?, val registradoEm: kotlin.time.Instant)

/** Tempo que o aviso "Refeição registrada · Desfazer" fica na tela (MOB-UX-04). */
const val PRAZO_DESFAZER_MS = 5_000L
