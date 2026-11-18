package org.br.dietamais.data.repositorio

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.br.dietamais.data.local.BancoDieta
import org.br.dietamais.data.remoto.JsonContrato
import org.br.dietamais.data.remoto.RespostaInvalida
import org.br.dietamais.data.remoto.SessaoAlterada
import org.br.dietamais.data.remoto.dto.OrientacaoResposta
import org.br.dietamais.data.remoto.dto.SuplementoResposta
import kotlin.uuid.Uuid

/** MOB-93: suplementos e orientações do paciente, lidos do banco local (vêm do /sync, C-09). Funciona offline. */
interface PortaPrescricoesPaciente {
    fun observarSuplementos(): Flow<List<SuplementoResposta>>
    fun observarOrientacoes(): Flow<List<OrientacaoResposta>>
    /** Suplementos com o lembrete desligado pelo paciente. Só existe no aparelho. */
    fun observarSemLembrete(): Flow<Set<Uuid>>
    fun definirLembrete(suplemento: Uuid, ligado: Boolean)
    /** Orientações que o paciente já abriu neste aparelho (para o selo "Nova"). */
    fun observarVistas(): Flow<Set<Uuid>>
    fun marcarVistas(ids: Collection<Uuid>)
}

/**
 * Lê `prescricoesSuplemento` e `orientacoes` de `registro_sync`. As escolhas locais ficam em coleções próprias do aparelho
 * (`lembretes-suplemento` com os desligados e `orientacoes-vistas`), uma linha por id, que o sync não toca.
 */
class PrescricoesPacienteRepositorio(private val banco: BancoDieta) : PortaPrescricoesPaciente {
    override fun observarSuplementos(): Flow<List<SuplementoResposta>> = lista("prescricoesSuplemento") { JsonContrato.decodeFromString<SuplementoResposta>(it) }
    override fun observarOrientacoes(): Flow<List<OrientacaoResposta>> = lista("orientacoes") { JsonContrato.decodeFromString<OrientacaoResposta>(it) }
    override fun observarSemLembrete(): Flow<Set<Uuid>> = ids(SEM_LEMBRETE)
    override fun observarVistas(): Flow<Set<Uuid>> = ids(VISTAS)

    override fun definirLembrete(suplemento: Uuid, ligado: Boolean) {
        val s = banco.sessaoQueries.atual().executeAsOneOrNull() ?: throw SessaoAlterada()
        if (ligado) banco.registroSyncQueries.remover(s.usuario_id, s.perfil, SEM_LEMBRETE, suplemento.toString())
        else banco.registroSyncQueries.salvar(s.usuario_id, s.perfil, SEM_LEMBRETE, suplemento.toString(), 0, 0, suplemento.toString())
    }

    override fun marcarVistas(ids: Collection<Uuid>) {
        if (ids.isEmpty()) return
        val s = banco.sessaoQueries.atual().executeAsOneOrNull() ?: throw SessaoAlterada()
        banco.transaction { ids.forEach { banco.registroSyncQueries.salvar(s.usuario_id, s.perfil, VISTAS, it.toString(), 0, 0, it.toString()) } }
    }

    private fun <T> lista(colecao: String, ler: (String) -> T): Flow<List<T>> =
        banco.registroSyncQueries.daSessao(colecao).asFlow().mapToList(Dispatchers.Default).map { linhas ->
            try { linhas.map(ler) } catch (_: Exception) { throw RespostaInvalida() }
        }

    private fun ids(colecao: String): Flow<Set<Uuid>> = banco.registroSyncQueries.daSessao(colecao).asFlow().mapToList(Dispatchers.Default)
        .map { linhas -> linhas.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }.toSet() }

    private companion object {
        const val SEM_LEMBRETE = "lembretes-suplemento"
        const val VISTAS = "orientacoes-vistas"
    }
}
