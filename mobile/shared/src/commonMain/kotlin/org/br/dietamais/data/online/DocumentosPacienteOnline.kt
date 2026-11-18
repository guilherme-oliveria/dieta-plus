package org.br.dietamais.data.online

import kotlinx.coroutines.flow.Flow
import org.br.dietamais.data.remoto.ApiDieta
import org.br.dietamais.data.remoto.SessaoAlterada
import org.br.dietamais.data.remoto.dto.DocumentoPacienteResposta
import org.br.dietamais.data.remoto.dto.DocumentosPacienteResposta
import org.br.dietamais.data.remoto.dto.Perfil
import org.br.dietamais.data.repositorio.PortaDocumentosPaciente

/** Web do paciente (MOB-95): `GET /eu/documentos`, só em memória. */
class DocumentosPacienteOnline(private val api: ApiDieta) : PortaDocumentosPaciente {
    private val recurso = RecursoOnline(api.sessao) {
        val esperado = api.operacaoSessao.value?.takeIf { it.contexto.perfil == Perfil.PACIENTE } ?: throw SessaoAlterada()
        val r = api.obter<DocumentosPacienteResposta>("eu/documentos").itens
        if (api.operacaoSessao.value != esperado) throw SessaoAlterada()
        r
    }
    override fun observar(): Flow<List<DocumentoPacienteResposta>> = recurso.fluxo
    override suspend fun atualizar() = recurso.atualizar()
}
