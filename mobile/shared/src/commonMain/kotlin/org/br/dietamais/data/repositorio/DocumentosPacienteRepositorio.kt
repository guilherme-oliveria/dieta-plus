package org.br.dietamais.data.repositorio

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.br.dietamais.data.local.BancoDieta
import org.br.dietamais.data.remoto.JsonContrato
import org.br.dietamais.data.remoto.RespostaInvalida
import org.br.dietamais.data.remoto.dto.DocumentoPacienteResposta

/** MOB-95 (BE-79): documentos emitidos para o paciente; só metadados, o PDF vem pelo link assinado. */
interface PortaDocumentosPaciente {
    fun observar(): Flow<List<DocumentoPacienteResposta>>
    /** Web: busca `GET /eu/documentos`. No app o sync traz a coleção `documentos`, então não faz nada. */
    suspend fun atualizar() {}
}

/** App: lê a coleção `documentos` do banco local, então a lista aparece sem internet. */
class DocumentosPacienteRepositorio(private val banco: BancoDieta) : PortaDocumentosPaciente {
    override fun observar(): Flow<List<DocumentoPacienteResposta>> =
        banco.registroSyncQueries.daSessao("documentos").asFlow().mapToList(Dispatchers.Default).map { linhas ->
            try { linhas.map { JsonContrato.decodeFromString<DocumentoPacienteResposta>(it) } } catch (_: Exception) { throw RespostaInvalida() }
        }
}
