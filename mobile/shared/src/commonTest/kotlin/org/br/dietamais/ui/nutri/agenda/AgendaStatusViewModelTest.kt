package org.br.dietamais.ui.nutri.agenda

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.ErroApi
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.agenda.ErroConsulta
import kotlin.test.*
import kotlin.time.Instant

/** MOB-84 (RN-AGE-03/04): modalidade, link, textos e as ações Realizada e Faltou. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgendaStatusViewModelTest {
    private val fuso = TimeZone.of("America/Sao_Paulo")
    private val ana = pacienteFalso("Ana")
    private fun TestScope.vm(c: PortaConsultasFalsa, online: Boolean = true) =
        AgendaViewModel(c, PortaPacientesFalsa(listOf(ana)), StandardTestDispatcher(testScheduler), flowOf(online), { agoraFalso }, { fuso })
    private fun AgendaViewModel.preencher() { nova(); mudarPaciente(ana.id); mudarData("29/10/2026"); mudarHora("14:30") }

    @Test fun agendaOnlineComLinkEOrientacaoMesmoSemRede() = runTest {
        val c = PortaConsultasFalsa(); val vm = vm(c, online = false); advanceUntilIdle()
        vm.preencher(); vm.mudarTipo(TipoConsulta.AVALIACAO); vm.mudarModalidade(ModalidadeConsulta.ONLINE)
        vm.mudarLink(" https://meet.google.com/abc-defg-hij "); vm.mudarOrientacao(" Venha em jejum "); vm.mudarObservacao("Revisar adesão")
        vm.salvar(); advanceUntilIdle()
        val req = assertNotNull(c.criada)
        assertEquals(TipoConsulta.AVALIACAO, req.tipo); assertEquals(ModalidadeConsulta.ONLINE, req.modalidade); assertEquals("https://meet.google.com/abc-defg-hij", req.linkOnline)
        assertEquals("Venha em jejum", req.orientacaoPaciente); assertEquals("Revisar adesão", req.observacaoInterna)
        assertEquals(MensagemAgenda.AGENDADA, vm.estado.value.aviso)
    }

    @Test fun linkInvalidoNaoAgendaEPresencialNaoMandaLink() = runTest {
        val c = PortaConsultasFalsa(); val vm = vm(c); advanceUntilIdle()
        vm.preencher(); vm.mudarModalidade(ModalidadeConsulta.ONLINE); vm.mudarLink("http://meet.invalid/x"); vm.salvar(); advanceUntilIdle()
        assertNull(c.criada); assertEquals(setOf(ErroConsulta.LINK), vm.estado.value.errosFormulario)
        vm.mudarModalidade(ModalidadeConsulta.PRESENCIAL); vm.salvar(); advanceUntilIdle()
        val req = assertNotNull(c.criada); assertEquals(ModalidadeConsulta.PRESENCIAL, req.modalidade); assertNull(req.linkOnline); assertNull(req.orientacaoPaciente)
    }

    @Test fun editarSoOsTextosMandaSoOQueMudouETextoVazioLimpa() = runTest {
        val consulta = consultaFalsa(ana.id, Instant.parse("2026-10-29T17:30:00Z"), versao = 4).copy(modalidade = ModalidadeConsulta.PRESENCIAL,
            orientacaoPaciente = "Traga exames", observacaoInterna = "Nota antiga")
        val c = PortaConsultasFalsa(listOf(consulta)); val vm = vm(c); advanceUntilIdle()
        vm.remarcar(consulta.id); vm.mudarOrientacao("Traga os exames de sangue"); vm.mudarObservacao(""); vm.salvar(); advanceUntilIdle()
        val (id, versao, pedido) = assertNotNull(c.alterada)
        assertEquals(consulta.id, id); assertEquals(4, versao)
        assertEquals(AtualizarConsultaRequisicao(orientacaoPaciente = "Traga os exames de sangue", observacaoInterna = ""), pedido)
    }

    @Test fun mudarParaOnlineMandaModalidadeELink() = runTest {
        val consulta = consultaFalsa(ana.id, Instant.parse("2026-10-29T17:30:00Z")).copy(modalidade = ModalidadeConsulta.PRESENCIAL)
        val c = PortaConsultasFalsa(listOf(consulta)); val vm = vm(c); advanceUntilIdle()
        vm.remarcar(consulta.id); vm.mudarModalidade(ModalidadeConsulta.ONLINE); vm.mudarLink("https://zoom.us/j/1"); vm.salvar(); advanceUntilIdle()
        assertEquals(AtualizarConsultaRequisicao(modalidade = ModalidadeConsulta.ONLINE, linkOnline = "https://zoom.us/j/1"), c.alterada?.third)
    }

    @Test fun realizadaEFaltaGravamOStatusECorrigemEntreSi() = runTest {
        val consulta = consultaFalsa(ana.id, agoraFalso, versao = 2)
        val c = PortaConsultasFalsa(listOf(consulta)); val vm = vm(c, online = false); advanceUntilIdle()
        vm.marcar(consulta.id, StatusConsulta.REALIZADA); advanceUntilIdle()
        assertEquals(Triple(consulta.id, 2L, AtualizarConsultaRequisicao(status = StatusConsulta.REALIZADA)), c.alterada)
        assertEquals(MensagemAgenda.MARCADA_REALIZADA, vm.estado.value.aviso)
        vm.marcar(consulta.id, StatusConsulta.REALIZADA); advanceUntilIdle() // repetir não é transição
        assertEquals(2L, c.alterada?.second)
        vm.marcar(consulta.id, StatusConsulta.FALTA); advanceUntilIdle()
        assertEquals(Triple(consulta.id, 3L, AtualizarConsultaRequisicao(status = StatusConsulta.FALTA)), c.alterada)
        assertEquals(MensagemAgenda.MARCADA_FALTA, vm.estado.value.aviso)
    }

    @Test fun canceladaNaoMarcaERecusaDoServidorRecarrega() = runTest {
        val cancelada = consultaFalsa(ana.id, agoraFalso, status = StatusConsulta.CANCELADA); val agendada = consultaFalsa(ana.id, agoraFalso)
        val c = PortaConsultasFalsa(listOf(cancelada, agendada)); val vm = vm(c); vm.abrir(); advanceUntilIdle()
        vm.marcar(cancelada.id, StatusConsulta.REALIZADA); advanceUntilIdle(); assertNull(c.alterada)
        c.falha = ErroApi("TRANSICAO_STATUS_INVALIDA", "RN-AGE-03", emptyList(), 422, null, null)
        val antes = c.atualizacoes
        vm.marcar(agendada.id, StatusConsulta.FALTA); advanceUntilIdle()
        assertEquals(MensagemAgenda.TRANSICAO_INVALIDA, vm.estado.value.erro); assertTrue(c.atualizacoes > antes)
    }

    @Test fun linkRecusadoPeloServidorViraMensagem() = runTest {
        val c = PortaConsultasFalsa(); val vm = vm(c); advanceUntilIdle()
        vm.preencher(); c.falha = ErroApi("LINK_INVALIDO", "RN-AGE-04", emptyList(), 422, null, null); vm.salvar(); advanceUntilIdle()
        assertEquals(MensagemAgenda.LINK_INVALIDO, vm.estado.value.erro)
    }
}
