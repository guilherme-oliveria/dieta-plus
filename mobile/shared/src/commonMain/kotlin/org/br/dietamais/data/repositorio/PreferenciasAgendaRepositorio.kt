package org.br.dietamais.data.repositorio

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*

/** Lembrete das consultas na conta da nutri (BE-72, RN-AGE-05): leitura e gravação online, sem `ETag` (o último vale). */
interface PortaPreferenciasAgenda {
    suspend fun obter(): PreferenciasAgendaDto
    suspend fun salvar(preferencias: PreferenciasAgendaDto): PreferenciasAgendaDto
}

class PreferenciasAgendaRepositorio(private val api: ApiDieta) : PortaPreferenciasAgenda {
    override suspend fun obter(): PreferenciasAgendaDto {
        val contexto = contexto()
        return api.obter<PreferenciasAgendaDto>(CAMINHO).also { conferir(contexto) }
    }
    override suspend fun salvar(preferencias: PreferenciasAgendaDto): PreferenciasAgendaDto {
        val contexto = contexto()
        val resposta = api.requisitar(HttpMethod.Put, CAMINHO, JsonContrato.encodeToJsonElement(preferencias))
        return api.decodificar(resposta, serializer<PreferenciasAgendaDto>()).also { conferir(contexto) }
    }
    private fun contexto() = api.operacaoSessao.value?.takeIf { it.contexto.perfil == Perfil.NUTRICIONISTA } ?: throw SessaoAlterada()
    private fun conferir(esperado: OperacaoSessao) { if (api.operacaoSessao.value != esperado) throw SessaoAlterada() }
    private companion object { const val CAMINHO = "eu/nutri/preferencias-agenda" }
}
