package org.br.dietamais.ui.paciente.documentos

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.br.dietamais.data.remoto.FalhaComunicacao
import org.br.dietamais.data.remoto.dto.DocumentoPacienteResposta
import org.br.dietamais.data.remoto.dto.TipoDocumento
import org.br.dietamais.data.repositorio.PortaDocumentosPaciente
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentosPacienteViewModelTest {
    private fun doc(titulo: String, dia: Int, anulado: Boolean = false) = DocumentoPacienteResposta(Uuid.random(), TipoDocumento.ATESTADO, titulo,
        Instant.parse("2026-11-${dia.toString().padStart(2, '0')}T12:00:00Z"), anulado, 1)

    private class Porta : PortaDocumentosPaciente {
        val lista = MutableStateFlow<List<DocumentoPacienteResposta>>(emptyList()); var falha: Exception? = null; var atualizacoes = 0
        override fun observar() = lista
        override suspend fun atualizar() { atualizacoes++; falha?.let { throw it } }
    }

    @Test fun listaDoBancoDaMaisNova() = runTest {
        val p = Porta().apply { lista.value = listOf(doc("Atestado", 3), doc("Declaração", 17), doc("Antigo", 1, anulado = true)) }
        val vm = DocumentosPacienteViewModel(p, StandardTestDispatcher(testScheduler)); advanceUntilIdle()
        assertTrue(vm.estado.value.carregado)
        assertEquals(listOf("Declaração", "Atestado", "Antigo"), vm.estado.value.documentos.map { it.titulo })
    }

    @Test fun semRedeMarcaEAtualizaQuandoVolta() = runTest {
        val p = Porta().apply { falha = FalhaComunicacao() }
        val online = MutableStateFlow(false)
        val vm = DocumentosPacienteViewModel(p, StandardTestDispatcher(testScheduler), online); advanceUntilIdle()
        assertTrue(vm.estado.value.semConexao); assertFalse(vm.estado.value.erro)
        p.falha = null; online.value = true; advanceUntilIdle()
        assertFalse(vm.estado.value.semConexao); assertEquals(2, p.atualizacoes)
    }
}
