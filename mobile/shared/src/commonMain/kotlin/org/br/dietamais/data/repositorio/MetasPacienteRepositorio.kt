package org.br.dietamais.data.repositorio

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.br.dietamais.data.local.BancoDieta
import org.br.dietamais.data.remoto.JsonContrato
import org.br.dietamais.data.remoto.RespostaInvalida
import org.br.dietamais.data.remoto.dto.MetaResposta
import org.br.dietamais.data.remoto.dto.RegistroMetaRequisicao
import org.br.dietamais.data.remoto.dto.RegistroMetaResposta
import org.br.dietamais.data.sync.FilaMutacoes
import org.br.dietamais.data.sync.NovaMutacao
import org.br.dietamais.data.sync.OperacaoMutacao
import org.br.dietamais.data.sync.novoIdLocal
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class DadosMetasPaciente(val metas: List<MetaResposta> = emptyList(), val registros: List<RegistroMetaResposta> = emptyList())

/** MOB-90: metas do paciente (lidas do sync, a nutri escreve online) e registros dele, gravados offline por inclusão (RN-MET-02). */
interface PortaMetasPaciente {
    fun observar(): Flow<DadosMetasPaciente>
    /** Emite quando o servidor recusa um registro já mostrado. */
    val falhas: Flow<Unit> get() = kotlinx.coroutines.flow.emptyFlow()
    /** Grava o registro local e a mutação na mesma transação (RN-SYN-01). */
    fun registrar(metaId: Uuid, data: LocalDate, valor: Double, agora: Instant)
}

/** Valor aceito num registro de meta (RN-MET-02): maior que zero e até 100.000 (a água, inteira de 1 a 5.000 ml, é registrada pelo cartão de água: MOB-99). */
const val VALOR_MAXIMO_REGISTRO_META = 100_000.0

class MetasPacienteRepositorio(private val banco: BancoDieta, private val fila: FilaMutacoes = FilaMutacoes(banco)) : PortaMetasPaciente {
    override fun observar(): Flow<DadosMetasPaciente> = combine(
        banco.registroSyncQueries.daSessao("metas").asFlow().mapToList(Dispatchers.Default),
        banco.registroSyncQueries.daSessao("registrosMeta").asFlow().mapToList(Dispatchers.Default)) { metas, registros ->
        try { DadosMetasPaciente(metas.map { JsonContrato.decodeFromString<MetaResposta>(it) }, registros.map { JsonContrato.decodeFromString<RegistroMetaResposta>(it) }) }
        catch (_: Exception) { throw RespostaInvalida() }
    }

    override val falhas: Flow<Unit> = flow {
        var vistas: Set<String>? = null
        banco.mutacaoPendenteQueries.daSessaoAtual().asFlow().mapToList(Dispatchers.Default).collect { lista ->
            val agora = lista.filter { it.status == "REJEITADA" && it.entidade == "registrosMeta" }.map { it.id_mutacao }.toSet()
            val antes = vistas
            vistas = agora
            if (antes != null && (agora - antes).isNotEmpty()) emit(Unit)
        }
    }

    override fun registrar(metaId: Uuid, data: LocalDate, valor: Double, agora: Instant) {
        require(valor > 0 && valor <= VALOR_MAXIMO_REGISTRO_META && valor.isFinite())
        val id = novoIdLocal()
        val dados = JsonContrato.encodeToJsonElement(RegistroMetaRequisicao(id, metaId, data, valor, agora)).jsonObject
        fila.gravarEEnfileirar(NovaMutacao("registrosMeta", OperacaoMutacao.CRIAR, id, dados)) {
            val sessao = checkNotNull(sessaoQueries.atual().executeAsOneOrNull())
            registroSyncQueries.salvar(sessao.usuario_id, sessao.perfil, "registrosMeta", id.toString(), 0, 0,
                JsonContrato.encodeToString(RegistroMetaResposta(id, metaId, data, valor, agora)))
        }
    }
}
