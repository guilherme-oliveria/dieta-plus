package org.br.dietamais.data.repositorio

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.br.dietamais.data.local.BancoDieta
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.sync.EscritaOffline
import org.br.dietamais.data.sync.novoIdLocal
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Rascunho de dieta offline-first (MOB-29b). Criar e salvar gravam a dieta otimista no banco local e a mutação na fila na mesma
 * transação (com `versaoBase`); o servidor decide no /sync e devolve os totais oficiais no próximo pull (RN-DIE-05).
 * Enquanto isso, os totais são a prévia local que a tela calculou. Publicar e duplicar continuam online ([online]).
 *
 * Conflito e rejeição seguem o fluxo das pendências (MOB-30): rejeição de criação some com o rascunho, de edição volta ao anterior.
 * Excluir só vale para o rascunho que nunca chegou ao servidor: o backend não tem exclusão de dieta.
 */
class DietasOffline(private val online: PortaDietas, private val banco: BancoDieta, private val aoAlterar: () -> Unit = {},
    private val escrita: EscritaOffline = EscritaOffline(banco, aoEnfileirar = aoAlterar), private val agora: () -> Instant = { Clock.System.now() }) : PortaDietas {
    override val escritaOffline: Boolean get() = true

    override suspend fun listar(pacienteId: Uuid): List<DietaResposta> {
        val servidor = try { online.listar(pacienteId) } catch (_: FalhaComunicacao) { null }
        val sessao = sessao()
        if (servidor == null) return locais().filter { it.pacienteId == pacienteId }.ordenadas()
        servidor.forEach { guardar(sessao.usuario_id, sessao.perfil, it) }
        val abertos = pendentes()
        // a edição local ainda não enviada vale mais que o estado do servidor; o rascunho só local aparece junto
        val locais = locais().filter { it.pacienteId == pacienteId && it.id in abertos }.associateBy { it.id }
        return (servidor.map { locais[it.id] ?: it } + locais.values.filter { l -> servidor.none { it.id == l.id } }).ordenadas()
    }

    override suspend fun criar(pacienteId: Uuid, requisicao: SalvarDietaRequisicao, chave: Uuid): DietaResposta = criar(pacienteId, requisicao, chave, PreviaDieta())
    override suspend fun salvar(id: Uuid, versao: Long, requisicao: SalvarDietaRequisicao): DietaResposta = salvar(id, versao, requisicao, PreviaDieta())

    override suspend fun criar(pacienteId: Uuid, requisicao: SalvarDietaRequisicao, chave: Uuid, previa: PreviaDieta): DietaResposta {
        sessao()
        val id = requisicao.id ?: chave
        val pedido = requisicao.copy(id = id)
        val momento = agora()
        // vinculoId provisório (= pacienteId): o servidor traz o real no próximo pull
        val otimista = otimista(pedido, null, previa).copy(id = id, pacienteId = pacienteId, vinculoId = pacienteId, versao = 1, criadoEm = momento, atualizadoEm = momento)
        escrita.criar(COLECAO, id, dados(pedido, pacienteId), JsonContrato.encodeToString(otimista))
        return otimista
    }

    override suspend fun salvar(id: Uuid, versao: Long, requisicao: SalvarDietaRequisicao, previa: PreviaDieta): DietaResposta {
        val sessao = sessao()
        val atual = banco.registroSyncQueries.dadosDe(sessao.usuario_id, sessao.perfil, COLECAO, id.toString()).executeAsOneOrNull()?.dados_json
            ?.let { JsonContrato.decodeFromString<DietaResposta>(it) } ?: throw ErroApi("RECURSO_NAO_ENCONTRADO", null, emptyList(), 404, null, null)
        if (atual.status != StatusDieta.RASCUNHO) throw ErroApi("DIETA_NAO_EDITAVEL", null, emptyList(), 409, null, null)
        if (atual.versao != versao) throw ErroApi("CONFLITO_VERSAO", "RN-SYN-02", emptyList(), 412, null, null)
        val mutacoes = banco.mutacaoPendenteQueries.doRegistro(sessao.usuario_id, sessao.perfil, COLECAO, id.toString()).executeAsList()
        // com conflito aberto, a decisão vem antes: outra edição na fila passaria por cima da escolha
        if (mutacoes.any { it.status == "CONFLITO" }) throw ErroApi("CONFLITO_VERSAO", "RN-SYN-02", emptyList(), 412, null, null)
        val aberta = mutacoes.lastOrNull { it.status == "PENDENTE" }
        if (aberta != null) {
            // MOB-29b: uma mutação por rascunho. A edição nova substitui o conteúdo da que ainda não foi aceita, com a mesma versaoBase;
            // duas mutações em sequência deixariam a segunda passar por cima de outro aparelho quando a primeira desse conflito.
            val pedido = requisicao.copy(id = if (aberta.operacao == "CRIAR") id else null)
            val nova = otimista(pedido, atual, previa).copy(atualizadoEm = agora())
            banco.transaction {
                banco.mutacaoPendenteQueries.atualizarDados(dados(pedido, atual.pacienteId).toString(), aberta.id_mutacao, sessao.usuario_id, sessao.perfil)
                banco.registroSyncQueries.salvar(sessao.usuario_id, sessao.perfil, COLECAO, id.toString(), 0, 0, JsonContrato.encodeToString(nova))
            }
            aoAlterar()
            return nova
        }
        val pedido = requisicao.copy(id = null)
        val nova = otimista(pedido, atual, previa).copy(versao = versao + 1, atualizadoEm = agora())
        escrita.atualizar(COLECAO, id, versao, dados(pedido, atual.pacienteId), JsonContrato.encodeToString(nova))
        return nova
    }

    override suspend fun publicar(id: Uuid, versao: Long): DietaSnapshotResposta {
        // o servidor só conhece a versão depois do envio; publicar antes daria conflito
        if (id in pendentes()) throw RascunhoPendente()
        return online.publicar(id, versao)
    }
    override suspend fun duplicar(id: Uuid, chave: Uuid): DietaResposta {
        if (id in pendentes()) throw RascunhoPendente()
        return online.duplicar(id, chave)
    }

    override fun pendentesDeEnvio(): Set<Uuid> = pendentes()

    override fun rascunhosSoLocais(): Set<Uuid> {
        val sessao = banco.sessaoQueries.atual().executeAsOneOrNull() ?: return emptySet()
        return banco.mutacaoPendenteQueries.doEscopo(sessao.usuario_id, sessao.perfil).executeAsList()
            .filter { it.entidade == COLECAO && it.operacao == "CRIAR" && it.status == "PENDENTE" }.map { Uuid.parse(it.id) }
            .filter { banco.registroSyncQueries.revisao(sessao.usuario_id, sessao.perfil, COLECAO, it.toString()).executeAsOneOrNull() == 0L }.toSet()
    }

    override suspend fun descartarRascunhoLocal(id: Uuid): Boolean = banco.transactionWithResult {
        val sessao = banco.sessaoQueries.atual().executeAsOneOrNull() ?: return@transactionWithResult false
        val mutacoes = banco.mutacaoPendenteQueries.doRegistro(sessao.usuario_id, sessao.perfil, COLECAO, id.toString()).executeAsList()
        val revisao = banco.registroSyncQueries.revisao(sessao.usuario_id, sessao.perfil, COLECAO, id.toString()).executeAsOneOrNull()
        // só o rascunho que nunca saiu do aparelho: criação ainda na fila e nenhuma revisão do servidor
        if (revisao != 0L || mutacoes.none { it.operacao == "CRIAR" && it.status == "PENDENTE" }) return@transactionWithResult false
        // uma mutação recusada ou em conflito vai pela tela de pendências, que mostra o motivo
        if (mutacoes.any { it.status != "PENDENTE" }) return@transactionWithResult false
        banco.mutacaoPendenteQueries.removerDoRegistro(sessao.usuario_id, sessao.perfil, COLECAO, id.toString())
        mutacoes.forEach { banco.conflitoQueries.remover(it.id_mutacao, sessao.usuario_id, sessao.perfil) }
        banco.registroSyncQueries.remover(sessao.usuario_id, sessao.perfil, COLECAO, id.toString())
        true
    }

    // ---- internos
    private fun sessao() = banco.sessaoQueries.atual().executeAsOneOrNull()?.takeIf { it.perfil == Perfil.NUTRICIONISTA.name } ?: throw SessaoAlterada()

    private fun pendentes(): Set<Uuid> {
        val sessao = banco.sessaoQueries.atual().executeAsOneOrNull() ?: return emptySet()
        return banco.mutacaoPendenteQueries.doEscopo(sessao.usuario_id, sessao.perfil).executeAsList()
            .filter { it.entidade == COLECAO && it.status in ABERTAS }.map { Uuid.parse(it.id) }.toSet()
    }

    private fun locais(): List<DietaResposta> =
        banco.registroSyncQueries.daSessao(COLECAO).executeAsList().mapNotNull { json ->
            try { JsonContrato.decodeFromString<DietaResposta>(json) } catch (_: Exception) { null }
        }

    /** Funde a resposta da API no banco local sem passar por cima de edição aberta nem de versão mais nova. */
    private fun guardar(usuario: String, perfil: String, dieta: DietaResposta) = banco.transaction {
        if (banco.mutacaoPendenteQueries.abertaDoRegistro(usuario, perfil, COLECAO, dieta.id.toString()).executeAsOne() > 0) return@transaction
        val revisao = banco.registroSyncQueries.revisao(usuario, perfil, COLECAO, dieta.id.toString()).executeAsOneOrNull()
        if (revisao != null && revisao > dieta.versao) return@transaction
        banco.registroSyncQueries.salvar(usuario, perfil, COLECAO, dieta.id.toString(), dieta.versao, 0, JsonContrato.encodeToString(dieta))
    }

    /** A mutação leva o `pacienteId` fora do DTO, como o /sync espera para dietas. */
    private fun dados(pedido: SalvarDietaRequisicao, pacienteId: Uuid): JsonObject =
        JsonObject(JsonContrato.encodeToJsonElement(pedido).jsonObject + ("pacienteId" to JsonPrimitive(pacienteId.toString())))

    private fun otimista(pedido: SalvarDietaRequisicao, atual: DietaResposta?, previa: PreviaDieta): DietaResposta {
        val nomesAntigos = atual?.refeicoes.orEmpty().flatMap { it.itens }.associate { it.id to it.nomeAlimento }
        val zero = TotaisNutricionaisResposta(0.0, 0.0, 0.0, 0.0)
        val refeicoes = pedido.refeicoes.map { r ->
            RefeicaoResposta(r.id ?: novoIdLocal(), r.nome, r.horario, r.ordem, r.itens.map { i ->
                val id = i.id ?: novoIdLocal()
                ItemRefeicaoResposta(id, i.alimentoId, i.grupoId, previa.nomes[id] ?: nomesAntigos[id], i.porcoes, null, i.gramas, i.observacao, zero,
                    opcao = i.grupoId?.let { g -> pedido.porcoes?.firstOrNull { it.grupoId == g }?.opcao }, aVontade = i.aVontade, textoLivre = i.textoLivre,
                    produtoId = i.produtoId)
            }, zero, tipo = r.tipo, alternativaDe = r.alternativaDe)
        }
        val base = atual ?: DietaResposta(novoIdLocal(), novoIdLocal(), novoIdLocal(), "", StatusDieta.RASCUNHO, null, emptyList(), zero, 1, agora(), agora())
        return base.copy(titulo = pedido.titulo, orientacao = pedido.orientacao, refeicoes = refeicoes, status = StatusDieta.RASCUNHO,
            totais = previa.totais ?: atual?.totais ?: zero, porcoes = pedido.porcoes, metas = pedido.metas, rodape = pedido.rodape)
    }

    private fun List<DietaResposta>.ordenadas() = sortedWith(compareByDescending<DietaResposta> { it.criadoEm }.thenByDescending { it.id.toString() })

    private companion object {
        const val COLECAO = "dietas"
        val ABERTAS = setOf("PENDENTE", "CONFLITO")
    }
}

/** O rascunho ainda está na fila: publicar ou duplicar só depois do envio. */
class RascunhoPendente : Exception("Rascunho ainda não enviado ao servidor.")
