package org.br.dietamais.data.repositorio

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
 * Modelos de questionário da nutri offline-first (MOB-29c). Criar e salvar gravam o modelo no banco local e a mutação `modelos` na fila,
 * na mesma transação; o servidor decide no /sync. O resto da anamnese (enviar questionário, ler respostas) continua em [online].
 *
 * - Uma mutação aberta por modelo: a edição nova substitui o conteúdo da que ainda não foi aceita (mesma `versaoBase`).
 * - O backend não tem exclusão de modelo de questionário (o /sync recusa `EXCLUIR` fora de `modelosDieta`, RN-SYN-04).
 * - Enviar ao paciente um modelo que ainda está na fila não dá: o servidor não o conhece. A tela desabilita e o ViewModel avisa.
 */
class ModelosQuestionarioOffline(private val online: PortaAnamnese, private val banco: BancoDieta, private val aoAlterar: () -> Unit = {},
    private val escrita: EscritaOffline = EscritaOffline(banco, aoEnfileirar = aoAlterar),
    private val agora: () -> Instant = { Clock.System.now() }) : PortaAnamnese by online {
    override val escritaOffline: Boolean get() = true

    override suspend fun criarModelo(requisicao: SalvarModeloRequisicao): ModeloQuestionarioResposta {
        sessao()
        val id = requisicao.id ?: novoIdLocal()
        val pedido = requisicao.copy(id = id)
        val momento = agora()
        val otimista = ModeloQuestionarioResposta(id, pedido.nome, padrao = false, blocos = pedido.blocos, versao = 1, criadoEm = momento, atualizadoEm = momento)
        escrita.criar(COLECAO, id, dados(pedido), JsonContrato.encodeToString(otimista))
        return otimista
    }

    override suspend fun salvarModelo(id: Uuid, versao: Long, requisicao: SalvarModeloRequisicao): ModeloQuestionarioResposta {
        val sessao = sessao()
        val usuario = sessao.usuario_id; val perfil = sessao.perfil
        val atual = banco.registroSyncQueries.dadosDe(usuario, perfil, COLECAO, id.toString()).executeAsOneOrNull()?.dados_json
            ?.let { try { JsonContrato.decodeFromString<ModeloQuestionarioResposta>(it) } catch (_: Exception) { null } }
            ?: throw ErroApi("RECURSO_NAO_ENCONTRADO", null, emptyList(), 404, null, null)
        val mutacoes = banco.mutacaoPendenteQueries.doRegistro(usuario, perfil, COLECAO, id.toString()).executeAsList()
        // com conflito aberto, a decisão nas pendências vem antes: outra edição na fila passaria por cima da escolha
        if (mutacoes.any { it.status == "CONFLITO" } || atual.versao != versao) throw ErroApi("CONFLITO_VERSAO", "RN-SYN-02", emptyList(), 412, null, null)
        val pedido = requisicao.copy(id = id)
        val aberta = mutacoes.lastOrNull { it.status == "PENDENTE" }
        if (aberta != null) {
            val nova = atual.copy(nome = pedido.nome, blocos = pedido.blocos, atualizadoEm = agora())
            banco.transaction {
                banco.mutacaoPendenteQueries.atualizarDados(dados(pedido).toString(), aberta.id_mutacao, usuario, perfil)
                banco.registroSyncQueries.salvar(usuario, perfil, COLECAO, id.toString(), 0, 0, JsonContrato.encodeToString(nova))
            }
            aoAlterar()
            return nova
        }
        val nova = atual.copy(nome = pedido.nome, blocos = pedido.blocos, versao = versao + 1, atualizadoEm = agora())
        escrita.atualizar(COLECAO, id, versao, dados(pedido), JsonContrato.encodeToString(nova))
        return nova
    }

    override fun modelosPendentes(): Set<Uuid> {
        val sessao = banco.sessaoQueries.atual().executeAsOneOrNull() ?: return emptySet()
        return banco.mutacaoPendenteQueries.doEscopo(sessao.usuario_id, sessao.perfil).executeAsList()
            .filter { it.entidade == COLECAO && it.status in ABERTAS }.map { Uuid.parse(it.id) }.toSet()
    }

    /** Enviar exige que o servidor já conheça o modelo. */
    override suspend fun enviar(pacienteId: Uuid, requisicao: EnviarQuestionarioRequisicao): QuestionarioResposta {
        if (requisicao.modeloId in modelosPendentes()) throw ModeloQuestionarioPendente()
        return online.enviar(pacienteId, requisicao)
    }

    private fun sessao() = banco.sessaoQueries.atual().executeAsOneOrNull()?.takeIf { it.perfil == Perfil.NUTRICIONISTA.name } ?: throw SessaoAlterada()
    private fun dados(pedido: SalvarModeloRequisicao) = JsonContrato.encodeToJsonElement(pedido).jsonObject

    private companion object {
        const val COLECAO = "modelos"
        val ABERTAS = setOf("PENDENTE", "CONFLITO")
    }
}

/** O modelo de questionário ainda está na fila: enviar ao paciente só depois do envio. */
class ModeloQuestionarioPendente : Exception("Modelo de questionário ainda não enviado ao servidor.")
