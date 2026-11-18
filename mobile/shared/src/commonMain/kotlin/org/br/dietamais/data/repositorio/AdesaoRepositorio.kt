package org.br.dietamais.data.repositorio

import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.AdesaoDto
import org.br.dietamais.data.remoto.dto.Perfil
import kotlin.uuid.Uuid

/** Adesão do paciente na ficha da nutri (BE-73, MOB-89): leitura online, sem cache. */
interface PortaAdesao {
    suspend fun obter(pacienteId: Uuid): AdesaoDto
}

class AdesaoRepositorio(private val api: ApiDieta) : PortaAdesao {
    override suspend fun obter(pacienteId: Uuid): AdesaoDto {
        val contexto = api.operacaoSessao.value?.takeIf { it.contexto.perfil == Perfil.NUTRICIONISTA } ?: throw SessaoAlterada()
        return api.obter<AdesaoDto>("pacientes/$pacienteId/adesao").also { if (api.operacaoSessao.value != contexto) throw SessaoAlterada() }
    }
}
