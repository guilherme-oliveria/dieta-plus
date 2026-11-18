package org.br.dietamais.ui.nutri.agenda

import androidx.compose.runtime.Composable
import kotlinx.datetime.DayOfWeek
import org.br.dietamais.data.remoto.dto.ModalidadeConsulta
import org.br.dietamais.data.remoto.dto.StatusConsulta
import org.br.dietamais.data.remoto.dto.TipoConsulta
import org.br.dietamais.dominio.agenda.ErroConsulta
import org.br.dietamais.recursos.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun textoMensagemAgenda(mensagem: MensagemAgenda): String = stringResource(when (mensagem) {
    MensagemAgenda.SEM_CONEXAO -> Res.string.agenda_msg_sem_conexao
    MensagemAgenda.CONFLITO -> Res.string.agenda_msg_conflito
    MensagemAgenda.ASSINATURA_INATIVA -> Res.string.agenda_msg_assinatura
    MensagemAgenda.VINCULO_ENCERRADO -> Res.string.agenda_msg_vinculo
    MensagemAgenda.NAO_ENCONTRADA -> Res.string.agenda_msg_nao_encontrada
    MensagemAgenda.JA_CANCELADA -> Res.string.agenda_msg_ja_cancelada
    MensagemAgenda.TAXA_EXCEDIDA -> Res.string.agenda_msg_taxa
    MensagemAgenda.DATA_INVALIDA -> Res.string.agenda_msg_data_invalida
    MensagemAgenda.ERRO -> Res.string.agenda_msg_erro
    MensagemAgenda.AGENDADA -> Res.string.agenda_msg_agendada
    MensagemAgenda.REMARCADA -> Res.string.agenda_msg_remarcada
    MensagemAgenda.CANCELADA -> Res.string.agenda_msg_cancelada
    MensagemAgenda.MARCADA_REALIZADA -> Res.string.agenda_msg_realizada
    MensagemAgenda.MARCADA_FALTA -> Res.string.agenda_msg_falta
    MensagemAgenda.TRANSICAO_INVALIDA -> Res.string.agenda_msg_transicao
    MensagemAgenda.LINK_INVALIDO -> Res.string.agenda_erro_link
})

@Composable
fun rotuloModalidade(modalidade: ModalidadeConsulta): String = stringResource(when (modalidade) {
    ModalidadeConsulta.ONLINE -> Res.string.agenda_modalidade_online
    else -> Res.string.agenda_modalidade_presencial
})

/** Rótulo do status para a nutri; nulo = Agendada (sem selo). */
@Composable
fun rotuloStatusConsulta(status: StatusConsulta): String? = when (status) {
    StatusConsulta.REALIZADA -> stringResource(Res.string.agenda_status_realizada)
    StatusConsulta.FALTA -> stringResource(Res.string.agenda_status_falta)
    StatusConsulta.CANCELADA -> stringResource(Res.string.agenda_cancelada)
    StatusConsulta.ENCERRADA -> stringResource(Res.string.agenda_status_encerrada)
    else -> null
}

@Composable
fun rotuloTipoConsulta(tipo: TipoConsulta): String = stringResource(when (tipo) {
    TipoConsulta.PRIMEIRA -> Res.string.agenda_tipo_primeira
    TipoConsulta.RETORNO -> Res.string.agenda_tipo_retorno
    TipoConsulta.ONLINE -> Res.string.agenda_tipo_online
    TipoConsulta.AVALIACAO -> Res.string.agenda_tipo_avaliacao
    TipoConsulta.OUTRO -> Res.string.agenda_tipo_outro
})

@Composable
fun textoErroConsulta(erro: ErroConsulta): String = stringResource(when (erro) {
    ErroConsulta.PACIENTE -> Res.string.agenda_erro_paciente
    ErroConsulta.DATA -> Res.string.agenda_erro_data
    ErroConsulta.HORA -> Res.string.agenda_erro_hora
    ErroConsulta.DURACAO -> Res.string.agenda_erro_duracao
    ErroConsulta.PASSADO -> Res.string.agenda_erro_passado
    ErroConsulta.LINK -> Res.string.agenda_erro_link
})

@Composable
fun nomeDiaSemana(dia: DayOfWeek): String = stringResource(when (dia) {
    DayOfWeek.MONDAY -> Res.string.agenda_dia_seg
    DayOfWeek.TUESDAY -> Res.string.agenda_dia_ter
    DayOfWeek.WEDNESDAY -> Res.string.agenda_dia_qua
    DayOfWeek.THURSDAY -> Res.string.agenda_dia_qui
    DayOfWeek.FRIDAY -> Res.string.agenda_dia_sex
    DayOfWeek.SATURDAY -> Res.string.agenda_dia_sab
    DayOfWeek.SUNDAY -> Res.string.agenda_dia_dom
})
