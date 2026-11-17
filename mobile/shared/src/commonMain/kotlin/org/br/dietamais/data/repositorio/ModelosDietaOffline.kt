package org.br.dietamais.data.repositorio

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.br.dietamais.data.local.BancoDieta
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.sync.ANTERIOR
import org.br.dietamais.data.sync.EscritaOffline
import org.br.dietamais.data.sync.FilaMutacoes
import org.br.dietamais.data.sync.NovaMutacao
import org.br.dietamais.data.sync.OperacaoMutacao
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Modelos de dieta da nutri offline-first (MOB-29d, BE-83). Criar, editar, duplicar e excluir gravam o modelo no banco local e a mutação
 * `modelosDieta` na fila, na mesma transação; o servidor decide no /sync (RN-MOD-01: o do sistema nunca muda, o limite de modelos é dele).
 *
 * - Uma mutação aberta por modelo: a edição nova substitui o conteúdo da que ainda não foi aceita (mesma `versaoBase`), como no rascunho de
 *   dieta (MOB-29b), para que um conflito não deixe outra edição passar por cima.
 * - Excluir um modelo que nunca saiu do aparelho só apaga o local; os demais vão como `EXCLUIR` e somem da lista na hora. Se o servidor
 *   recusar, o modelo volta (instantâneo anterior).
 * - Os modelos do sistema vêm só pelo GET: duplicar um deles continua online. Aplicar e salvar dieta como modelo também.
 */
class ModelosDietaOffline(private val online: PortaModelosDieta, private val banco: BancoDieta, private val aoAlterar: () -> Unit = {},
    private val escrita: EscritaOffline = EscritaOffline(banco, aoEnfileirar = aoAlterar), private val fila: FilaMutacoes = FilaMutacoes(banco),
    private val agora: () -> Instant = { Clock.System.now() }) : PortaModelosDieta {
    override val escritaOffline: Boolean get() = true

    override suspend fun listar(): List<ModeloDietaResposta> {
        val sessao = sessao()
        // sem rede o repositório online já cai nos modelos locais (MOB-56c); aqui só falha se não houver nenhum
        val servidor = try { online.listar() } catch (falha: FalhaComunicacao) { return lerModelosLocais(banco).ifEmpty { throw falha } }
        servidor.filterNot { it.doSistema }.forEach { guardar(sessao.usuario_id, sessao.perfil, it) }
        val abertos = abertos()
        val excluidos = abertos.filterValues { it == OperacaoMutacao.EXCLUIR.name }.keys
        // a edição local ainda não aceita vale mais que o estado do servidor; o modelo só local aparece junto
        val locais = lerModelosLocais(banco).filter { it.id in abertos }.associateBy { it.id }
        return servidor.filterNot { it.id in excluidos }.map { locais[it.id] ?: it } + locais.values.filter { l -> servidor.none { it.id == l.id } }
    }

    override suspend fun criar(requisicao: SalvarModeloDietaRequisicao, chave: Uuid): ModeloDietaResposta {
        sessao()
        val id = requisicao.id ?: chave
        val pedido = requisicao.copy(id = id)
        val momento = agora()
        val otimista = otimista(pedido, id, versao = 1, criadoEm = momento)
        escrita.criar(COLECAO, id, dados(pedido), JsonContrato.encodeToString(otimista))
        return otimista
    }

    override suspend fun atualizar(id: Uuid, versao: Long, requisicao: SalvarModeloDietaRequisicao): ModeloDietaResposta {
        val sessao = sessao()
        val atual = local(sessao.usuario_id, sessao.perfil, id) ?: throw naoEncontrado()
        if (atual.doSistema) throw ErroApi("ACESSO_NEGADO", "RN-MOD-01", emptyList(), 403, null, null)
        if (atual.versao != versao) throw conflito()
        val mutacoes = banco.mutacaoPendenteQueries.doRegistro(sessao.usuario_id, sessao.perfil, COLECAO, id.toString()).executeAsList()
        if (mutacoes.any { it.status == "CONFLITO" }) throw conflito()
        val aberta = mutacoes.lastOrNull { it.status == "PENDENTE" }
        if (aberta?.operacao == OperacaoMutacao.EXCLUIR.name) throw naoEncontrado()
        if (aberta != null) {
            val pedido = requisicao.copy(id = if (aberta.operacao == OperacaoMutacao.CRIAR.name) id else null)
            val nova = otimista(pedido, id, atual.versao, atual.criadoEm)
            banco.transaction {
                banco.mutacaoPendenteQueries.atualizarDados(dados(pedido).toString(), aberta.id_mutacao, sessao.usuario_id, sessao.perfil)
                banco.registroSyncQueries.salvar(sessao.usuario_id, sessao.perfil, COLECAO, id.toString(), 0, 0, JsonContrato.encodeToString(nova))
            }
            aoAlterar()
            return nova
        }
        val pedido = requisicao.copy(id = null)
        val nova = otimista(pedido, id, versao + 1, atual.criadoEm)
        escrita.atualizar(COLECAO, id, versao, dados(pedido), JsonContrato.encodeToString(nova))
        return nova
    }

    /** RN-MOD-01: a cópia de um modelo dela nasce no aparelho com o mesmo sufixo que o servidor usa; a do sistema continua online. */
    override suspend fun duplicar(id: Uuid, chave: Uuid): ModeloDietaResposta {
        val sessao = sessao()
        val origem = local(sessao.usuario_id, sessao.perfil, id) ?: return online.duplicar(id, chave)
        val titulo = origem.titulo.take(141) + " (cópia)"
        return criar(SalvarModeloDietaRequisicao(null, titulo, origem.descricao, origem.condicao, origem.orientacao, origem.refeicoes, origem.porcoes,
            origem.metas, origem.rodape, origem.restricoes, origem.kcalReferencia), chave)
    }

    override suspend fun excluir(id: Uuid, versao: Long) {
        val sessao = sessao()
        val usuario = sessao.usuario_id; val perfil = sessao.perfil
        val atual = local(usuario, perfil, id) ?: throw naoEncontrado()
        if (atual.doSistema) throw ErroApi("ACESSO_NEGADO", "RN-MOD-01", emptyList(), 403, null, null)
        val mutacoes = banco.mutacaoPendenteQueries.doRegistro(usuario, perfil, COLECAO, id.toString()).executeAsList()
        if (mutacoes.any { it.status != "PENDENTE" }) throw conflito()
        val revisao = banco.registroSyncQueries.revisao(usuario, perfil, COLECAO, id.toString()).executeAsOneOrNull()
        val aberta = mutacoes.lastOrNull()
        when {
            // nunca saiu do aparelho: some daqui e da fila
            aberta?.operacao == OperacaoMutacao.CRIAR.name && revisao == 0L -> banco.transaction {
                banco.mutacaoPendenteQueries.removerDoRegistro(usuario, perfil, COLECAO, id.toString())
                banco.registroSyncQueries.remover(usuario, perfil, COLECAO, id.toString())
            }
            // edição ainda na fila: a mesma mutação passa a ser a exclusão (a versaoBase continua a do servidor)
            aberta != null -> banco.transaction {
                banco.mutacaoPendenteQueries.transformarEmExclusao(aberta.id_mutacao, usuario, perfil)
                banco.registroSyncQueries.remover(usuario, perfil, COLECAO, id.toString())
            }
            else -> {
                if (atual.versao != versao) throw conflito()
                fila.gravarEEnfileirar(NovaMutacao(COLECAO, OperacaoMutacao.EXCLUIR, id, JsonObject(emptyMap()), versao)) {
                    // o instantâneo devolve o modelo se o servidor recusar a exclusão
                    registroSyncQueries.salvar(usuario, perfil, ANTERIOR + COLECAO, id.toString(), 0, 0, JsonContrato.encodeToString(atual))
                    registroSyncQueries.remover(usuario, perfil, COLECAO, id.toString())
                }
            }
        }
        aoAlterar()
    }

    override suspend fun salvarComoModelo(dietaId: Uuid, requisicao: SalvarComoModeloRequisicao, chave: Uuid) = online.salvarComoModelo(dietaId, requisicao, chave)
    override suspend fun referenciaAvaliacao(pacienteId: Uuid) = online.referenciaAvaliacao(pacienteId)
    override suspend fun aplicar(pacienteId: Uuid, modeloId: Uuid, requisicao: AplicarModeloRequisicao, chave: Uuid): DietaDeModeloResposta {
        // o servidor só conhece o modelo depois do envio
        if (modeloId in abertos()) throw ModeloPendente()
        return online.aplicar(pacienteId, modeloId, requisicao, chave)
    }

    override fun pendentesDeEnvio(): Set<Uuid> = abertos().keys

    // ---- internos
    private fun sessao() = banco.sessaoQueries.atual().executeAsOneOrNull()?.takeIf { it.perfil == Perfil.NUTRICIONISTA.name } ?: throw SessaoAlterada()

    /** Modelos com mutação aberta (pendente ou em conflito) e a operação mais recente de cada um. */
    private fun abertos(): Map<Uuid, String> {
        val sessao = banco.sessaoQueries.atual().executeAsOneOrNull() ?: return emptyMap()
        return banco.mutacaoPendenteQueries.doEscopo(sessao.usuario_id, sessao.perfil).executeAsList()
            .filter { it.entidade == COLECAO && it.status in ABERTAS }.associate { Uuid.parse(it.id) to it.operacao }
    }

    private fun local(usuario: String, perfil: String, id: Uuid): ModeloDietaResposta? =
        banco.registroSyncQueries.dadosDe(usuario, perfil, COLECAO, id.toString()).executeAsOneOrNull()?.dados_json
            ?.let { try { JsonContrato.decodeFromString<ModeloDietaResposta>(it) } catch (_: Exception) { null } }

    /** Funde a resposta da API no banco local sem passar por cima de mudança aberta nem de versão mais nova. */
    private fun guardar(usuario: String, perfil: String, modelo: ModeloDietaResposta) = banco.transaction {
        if (banco.mutacaoPendenteQueries.abertaDoRegistro(usuario, perfil, COLECAO, modelo.id.toString()).executeAsOne() > 0) return@transaction
        val revisao = banco.registroSyncQueries.revisao(usuario, perfil, COLECAO, modelo.id.toString()).executeAsOneOrNull()
        if (revisao != null && revisao > modelo.versao) return@transaction
        banco.registroSyncQueries.salvar(usuario, perfil, COLECAO, modelo.id.toString(), modelo.versao, 0, JsonContrato.encodeToString(modelo))
    }

    private fun dados(pedido: SalvarModeloDietaRequisicao): JsonObject = JsonContrato.encodeToJsonElement(pedido).jsonObject

    private fun otimista(pedido: SalvarModeloDietaRequisicao, id: Uuid, versao: Long, criadoEm: Instant) = ModeloDietaResposta(id = id, escopo = "NUTRI",
        titulo = pedido.titulo, descricao = pedido.descricao, condicao = pedido.condicao, orientacao = pedido.orientacao, refeicoes = pedido.refeicoes,
        porcoes = pedido.porcoes, metas = pedido.metas, rodape = pedido.rodape, restricoes = pedido.restricoes.orEmpty(), kcalReferencia = pedido.kcalReferencia,
        versao = versao, criadoEm = criadoEm, atualizadoEm = agora())

    private fun naoEncontrado() = ErroApi("RECURSO_NAO_ENCONTRADO", null, emptyList(), 404, null, null)
    private fun conflito() = ErroApi("CONFLITO_VERSAO", "RN-SYN-02", emptyList(), 412, null, null)

    private companion object {
        const val COLECAO = "modelosDieta"
        val ABERTAS = setOf("PENDENTE", "CONFLITO")
    }
}

/** O modelo ainda está na fila: aplicar a um paciente só depois do envio. */
class ModeloPendente : Exception("Modelo ainda não enviado ao servidor.")
