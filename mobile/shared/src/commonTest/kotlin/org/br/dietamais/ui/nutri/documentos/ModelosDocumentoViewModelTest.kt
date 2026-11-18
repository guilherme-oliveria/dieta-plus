package org.br.dietamais.ui.nutri.documentos

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.br.dietamais.data.remoto.ErroApi
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.PortaDocumentosNutri
import org.br.dietamais.dominio.documentos.ErroModeloDoc
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class ModelosDocumentoViewModelTest {
    private val t = Instant.parse("2026-11-01T00:00:00Z")
    private val sistema = ModeloDocumentoResposta(Uuid.random(), TipoDocumento.ATESTADO, "Atestado", "Atesto {{paciente}}", true, 1, t, t)
    private val meu = ModeloDocumentoResposta(Uuid.random(), TipoDocumento.DECLARACAO_COMPARECIMENTO, "Academia", "Declaro {{paciente}}", false, 3, t, t)

    private class Porta(var lista: List<ModeloDocumentoResposta>) : PortaDocumentosNutri {
        var criado: ModeloDocumentoRequisicao? = null; var atualizado: Triple<Uuid, Long, ModeloDocumentoRequisicao>? = null
        var excluido: Pair<Uuid, Long>? = null; var falha: Exception? = null
        override suspend fun modelos() = lista
        override suspend fun criarModelo(requisicao: ModeloDocumentoRequisicao): ModeloDocumentoResposta {
            falha?.let { throw it }; criado = requisicao
            return ModeloDocumentoResposta(Uuid.random(), requisicao.tipo, requisicao.nome, requisicao.texto, false, 0, Instant.parse("2026-11-18T00:00:00Z"), Instant.parse("2026-11-18T00:00:00Z"))
        }
        override suspend fun atualizarModelo(id: Uuid, versao: Long, requisicao: ModeloDocumentoRequisicao): ModeloDocumentoResposta {
            falha?.let { throw it }; atualizado = Triple(id, versao, requisicao)
            return lista.first { it.id == id }.copy(nome = requisicao.nome, texto = requisicao.texto, versao = versao + 1)
        }
        override suspend fun excluirModelo(id: Uuid, versao: Long) { falha?.let { throw it }; excluido = id to versao }
        override suspend fun documentos(pacienteId: Uuid) = emptyList<DocumentoResposta>()
        override suspend fun emitir(pacienteId: Uuid, requisicao: EmitirDocumentoRequisicao) = error("não usado")
        override suspend fun anular(id: Uuid, motivo: String) = error("não usado")
    }

    private fun TestScope.vm(p: Porta) = ModelosDocumentoViewModel(p, StandardTestDispatcher(testScheduler))

    @Test fun copiarDoSistemaCriaUmModeloNovo() = runTest {
        val p = Porta(listOf(meu, sistema)); val vm = vm(p); vm.recarregar(); advanceUntilIdle()
        assertEquals(listOf(sistema, meu), vm.estado.value.modelos)
        vm.abrir(sistema.id)
        assertEquals("Atestado (cópia)", vm.estado.value.formulario!!.nome)
        vm.inserirCampo("data"); vm.salvar(); advanceUntilIdle()
        assertEquals(ModeloDocumentoRequisicao(TipoDocumento.ATESTADO, "Atestado (cópia)", "Atesto {{paciente}}{{data}}"), p.criado)
        assertNull(vm.estado.value.formulario); assertEquals(3, vm.estado.value.modelos!!.size)
        assertEquals(MensagemDocumentos.MODELO_SALVO, vm.estado.value.aviso)
    }

    @Test fun editarOMeuMandaAVersao() = runTest {
        val p = Porta(listOf(meu)); val vm = vm(p); vm.recarregar(); advanceUntilIdle()
        vm.abrir(meu.id); vm.mudarNome("Academia 2"); vm.salvar(); advanceUntilIdle()
        assertEquals(meu.id, p.atualizado!!.first); assertEquals(3L, p.atualizado!!.second)
        assertEquals("Academia 2", vm.estado.value.modelos!!.single().nome)
    }

    @Test fun validaAntesDeEnviar() = runTest {
        val p = Porta(listOf(meu)); val vm = vm(p); vm.recarregar(); advanceUntilIdle()
        vm.novo(); vm.salvar()
        assertEquals(setOf(ErroModeloDoc.NOME, ErroModeloDoc.TEXTO), vm.estado.value.erros); assertNull(p.criado)
    }

    @Test fun conflitoAtualizaAVersaoEMantemOTexto() = runTest {
        val p = Porta(listOf(meu)); val vm = vm(p); vm.recarregar(); advanceUntilIdle()
        vm.abrir(meu.id); vm.mudarTexto("Novo texto")
        p.falha = ErroApi("CONFLITO_VERSAO", null, emptyList(), 409, null, null); p.lista = listOf(meu.copy(versao = 4))
        vm.salvar(); advanceUntilIdle()
        assertEquals(MensagemDocumentos.CONFLITO, vm.estado.value.erro)
        assertEquals(4L, vm.estado.value.formulario!!.versao); assertEquals("Novo texto", vm.estado.value.formulario!!.texto)
    }

    @Test fun excluiSoOMeuComAVersao() = runTest {
        val p = Porta(listOf(sistema, meu)); val vm = vm(p); vm.recarregar(); advanceUntilIdle()
        vm.pedirExclusao(sistema.id); vm.excluir(); advanceUntilIdle()
        assertNull(p.excluido)
        vm.pedirExclusao(meu.id); vm.excluir(); advanceUntilIdle()
        assertEquals(meu.id to 3L, p.excluido); assertEquals(listOf(sistema), vm.estado.value.modelos)
    }
}
