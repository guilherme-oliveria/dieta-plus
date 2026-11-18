package org.br.dietamais.ui.nutri.documentos

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.ErroApi
import org.br.dietamais.data.remoto.FalhaComunicacao
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.data.repositorio.PortaConsultas
import org.br.dietamais.data.repositorio.PortaDocumentosNutri
import org.br.dietamais.data.repositorio.PortaPerfilNutri
import org.br.dietamais.dominio.documentos.ErroEmissao
import kotlin.test.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentosNutriViewModelTest {
    private val t = Instant.parse("2026-11-01T00:00:00Z")
    private val paciente = Uuid.random()
    private val declaracao = ModeloDocumentoResposta(Uuid.random(), TipoDocumento.DECLARACAO_COMPARECIMENTO, "Declaração de comparecimento",
        "Declaro que {{paciente}} compareceu em {{data}}, das {{horario}}.", true, 1, t, t)
    private val atestado = ModeloDocumentoResposta(Uuid.random(), TipoDocumento.ATESTADO, "Atestado", "Atesto que {{paciente}} esteve em {{data}}.", true, 1, t, t)
    private val realizada = ConsultaResposta(Uuid.random(), paciente, Uuid.random(), Instant.parse("2026-11-17T15:00:00Z"), 50, TipoConsulta.RETORNO,
        status = StatusConsulta.REALIZADA, versao = 1, criadoEm = t, atualizadoEm = t)

    private inner class Porta : PortaDocumentosNutri {
        val docs = mutableListOf<DocumentoResposta>(); var falha: Exception? = null
        var emitido: EmitirDocumentoRequisicao? = null; var motivo: String? = null; var leituras = 0
        override suspend fun modelos() = listOf(atestado, declaracao)
        override suspend fun criarModelo(requisicao: ModeloDocumentoRequisicao) = error("não usado")
        override suspend fun atualizarModelo(id: Uuid, versao: Long, requisicao: ModeloDocumentoRequisicao) = error("não usado")
        override suspend fun excluirModelo(id: Uuid, versao: Long) = error("não usado")
        override suspend fun documentos(pacienteId: Uuid): List<DocumentoResposta> { leituras++; falha?.let { throw it }; return docs.toList() }
        override suspend fun emitir(pacienteId: Uuid, requisicao: EmitirDocumentoRequisicao): DocumentoResposta {
            falha?.let { throw it }; emitido = requisicao
            return DocumentoResposta(requisicao.id!!, pacienteId, TipoDocumento.DECLARACAO_COMPARECIMENTO, "Declaração de comparecimento", "texto", "DM7K-42QX", Instant.parse("2026-11-18T10:00:00Z"))
        }
        override suspend fun anular(id: Uuid, motivo: String): DocumentoResposta {
            falha?.let { throw it }; this.motivo = motivo
            return docs.first { it.id == id }.copy(anuladoEm = Instant.parse("2026-11-18T11:00:00Z"), motivoAnulacao = motivo)
        }
    }
    private val consultas = object : PortaConsultas {
        override fun observar() = flowOf(listOf(realizada))
        override suspend fun atualizar(de: Instant, ate: Instant) {}
        override suspend fun criar(requisicao: CriarConsultaRequisicao) = error("não usado")
        override suspend fun alterar(id: Uuid, versao: Long, requisicao: AtualizarConsultaRequisicao) = error("não usado")
    }
    private val perfil = object : PortaPerfilNutri {
        override suspend fun obter() = PerfilNutriResposta(Uuid.random(), "Nut. Marina", "CRN-3 1234", versao = 1, atualizadoEm = t)
        override suspend fun salvar(versao: Long, requisicao: AtualizarPerfilNutriRequisicao) = error("não usado")
        override suspend fun enviarLogo(bytes: ByteArray, tipo: String) {}
        override suspend fun removerLogo() {}
    }
    private fun doc(titulo: String) = DocumentoResposta(Uuid.random(), paciente, TipoDocumento.ATESTADO, titulo, "x", "DM3P-8TWA", Instant.parse("2026-11-03T10:00:00Z"))

    private fun TestScope.vm(p: Porta, online: Flow<Boolean>? = null) = DocumentosNutriViewModel(p, consultas, perfil, StandardTestDispatcher(testScheduler), online,
        { TimeZone.UTC }, { LocalDate(2026, 11, 18) })

    @Test fun abreAListaDoPaciente() = runTest {
        val p = Porta().apply { docs += doc("Atestado") }
        val vm = vm(p); vm.abrir(paciente, "Ana Lima"); advanceUntilIdle()
        assertTrue(vm.estado.value.carregado); assertEquals(1, vm.estado.value.documentos.size)
    }

    @Test fun semRedeMostraErroERecarregaQuandoVolta() = runTest {
        val p = Porta().apply { falha = FalhaComunicacao() }
        val online = MutableStateFlow(false)
        val vm = vm(p, online); vm.abrir(paciente, "Ana"); advanceUntilIdle()
        assertEquals(MensagemDocumentos.SEM_CONEXAO, vm.estado.value.erro); assertFalse(vm.estado.value.carregado)
        p.falha = null; online.value = true; advanceUntilIdle()
        assertTrue(vm.estado.value.carregado)
    }

    @Test fun emitirDaConsultaEscolheADeclaracaoEAPreviaUsaAConsulta() = runTest {
        val p = Porta()
        val vm = vm(p); advanceUntilIdle()
        vm.emitirDaConsulta(realizada, "Ana Lima"); advanceUntilIdle()
        val f = vm.estado.value.emissao!!
        assertEquals(declaracao.id, f.modeloId); assertEquals(realizada.id, f.consultaId)
        assertEquals("Declaro que Ana Lima compareceu em 17/11/2026, das 15:00 às 15:50.", vm.previa()!!.joinToString("") { it.texto })
        vm.emitir(); advanceUntilIdle()
        assertEquals(realizada.id, p.emitido!!.consultaId); assertNull(p.emitido!!.data)
        assertNull(vm.estado.value.emissao); assertEquals(MensagemDocumentos.EMITIDO, vm.estado.value.aviso)
        assertEquals("DM7K-42QX", vm.estado.value.documentos.first().codigoConferencia)
    }

    @Test fun atestadoValidaDataETrocarParaDeclaracaoPegaAUltimaConsulta() = runTest {
        val p = Porta()
        val vm = vm(p); vm.abrir(paciente, "Ana"); advanceUntilIdle()
        vm.abrirEmissao(); advanceUntilIdle()
        assertEquals(atestado.id, vm.estado.value.emissao!!.modeloId)
        assertEquals("Nut. Marina", vm.estado.value.nutri)
        vm.mudarData("31/13/2026"); vm.emitir()
        assertEquals(setOf(ErroEmissao.DATA), vm.estado.value.errosEmissao); assertNull(p.emitido)
        vm.escolherModelo(declaracao.id)
        assertEquals(realizada.id, vm.estado.value.emissao!!.consultaId)
    }

    @Test fun anularPedeMotivoEAtualizaOCartao() = runTest {
        val d = doc("Atestado"); val p = Porta().apply { docs += d }
        val vm = vm(p); vm.abrir(paciente, "Ana"); advanceUntilIdle()
        vm.pedirAnulacao(d.id); vm.anular()
        assertTrue(vm.estado.value.erroMotivo); assertNull(p.motivo)
        vm.mudarMotivo("  Data da consulta errada "); vm.anular(); advanceUntilIdle()
        assertEquals("  Data da consulta errada ", p.motivo)
        assertNull(vm.estado.value.anulando); assertNotNull(vm.estado.value.documentos.first().anuladoEm)
        assertEquals(MensagemDocumentos.ANULADO, vm.estado.value.aviso)
    }

    @Test fun jaAnuladoFechaERecarrega() = runTest {
        val d = doc("Atestado"); val p = Porta().apply { docs += d }
        val vm = vm(p); vm.abrir(paciente, "Ana"); advanceUntilIdle()
        val antes = p.leituras
        vm.pedirAnulacao(d.id); vm.mudarMotivo("x")
        p.falha = ErroApi("DOCUMENTO_JA_ANULADO", "RN-DOC-02", emptyList(), 422, null, null)
        vm.anular(); advanceUntilIdle()
        assertEquals(MensagemDocumentos.JA_ANULADO, vm.estado.value.erro); assertNull(vm.estado.value.anulando)
        assertTrue(p.leituras > antes)
    }
}
