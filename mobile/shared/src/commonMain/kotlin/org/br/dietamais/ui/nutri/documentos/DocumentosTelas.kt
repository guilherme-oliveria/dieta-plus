package org.br.dietamais.ui.nutri.documentos

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.data.remoto.dto.ConsultaResposta
import org.br.dietamais.data.remoto.dto.DocumentoResposta
import org.br.dietamais.data.remoto.dto.TipoDocumento
import org.br.dietamais.data.repositorio.PortaPdf
import org.br.dietamais.data.repositorio.RecursoPdf
import org.br.dietamais.dominio.agenda.formatarData
import org.br.dietamais.dominio.agenda.formatarHora
import org.br.dietamais.dominio.documentos.ErroEmissao
import org.br.dietamais.dominio.documentos.TrechoPrevia
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AcoesDocumentos(val aoRecarregar: () -> Unit = {}, val aoEmitir: () -> Unit = {}, val aoModelo: (Uuid) -> Unit = {}, val aoConsulta: (Uuid) -> Unit = {},
    val aoData: (String) -> Unit = {}, val aoHorario: (String) -> Unit = {}, val aoConfirmarEmissao: () -> Unit = {}, val aoFecharEmissao: () -> Unit = {},
    val aoAnular: (Uuid) -> Unit = {}, val aoMotivo: (String) -> Unit = {}, val aoConfirmarAnulacao: () -> Unit = {}, val aoDesistirAnulacao: () -> Unit = {})

fun acoesDocumentos(vm: DocumentosNutriViewModel) = AcoesDocumentos(vm::recarregar, { vm.abrirEmissao() }, vm::escolherModelo, vm::escolherConsulta, vm::mudarData,
    vm::mudarHorario, vm::emitir, vm::fecharEmissao, vm::pedirAnulacao, vm::mudarMotivo, vm::anular, vm::desistirDaAnulacao)

/** Aba Documentos da ficha (MOB-94): lista, emitir, compartilhar o PDF e anular. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentosPacienteRota(vm: DocumentosNutriViewModel, pacienteId: Uuid, nomePaciente: String, pdf: PortaPdf) {
    val estado by vm.estado.collectAsState()
    LaunchedEffect(pacienteId) { vm.abrir(pacienteId, nomePaciente) }
    val acoes = remember(vm) { acoesDocumentos(vm) }
    DocumentosPacienteTela(estado, acoes, compartilhar = { id -> BotaoPdf(stringResource(Res.string.doc_compartilhar), !estado.semConexao, { pdf.link(RecursoPdf.DOCUMENTO, id) }) })
    FolhasDocumento(vm, estado, acoes)
}

/** Folhas de emitir e de anular; a agenda usa a mesma para a declaração da consulta realizada. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhasDocumento(vm: DocumentosNutriViewModel, estado: EstadoDocumentos, acoes: AcoesDocumentos = remember(vm) { acoesDocumentos(vm) }) {
    if (estado.emissao != null) ModalBottomSheet(onDismissRequest = vm::fecharEmissao) { EmitirDocumentoConteudo(estado, vm.previa(), acoes) }
    if (estado.anulando != null) ModalBottomSheet(onDismissRequest = vm::desistirDaAnulacao) { AnularDocumentoConteudo(estado, acoes) }
}

@Composable
fun DocumentosPacienteTela(estado: EstadoDocumentos, acoes: AcoesDocumentos, modifier: Modifier = Modifier,
    compartilhar: @Composable (Uuid) -> Unit = {}, fuso: TimeZone = TimeZone.currentSystemDefault()) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.doc_emitidos_para, primeiroNome(estado.nomePaciente)), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() })
            BotaoPrincipal(stringResource(Res.string.doc_emitir), acoes.aoEmitir, habilitado = !estado.semConexao && estado.carregado)
        }
        if (estado.semConexao) Text(stringResource(Res.string.doc_msg_sem_conexao), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Mensagens(estado)
        when {
            estado.carregando && !estado.carregado -> EstadoCarregando()
            !estado.carregado -> if (estado.erro != null) EstadoErro(acoes.aoRecarregar)
            estado.documentos.isEmpty() -> Text(stringResource(Res.string.doc_vazio), style = MaterialTheme.typography.bodyMedium)
            else -> estado.documentos.forEach { CartaoDocumento(it, estado, acoes, compartilhar, fuso) }
        }
        Text(stringResource(Res.string.doc_nota_imutavel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CartaoDocumento(d: DocumentoResposta, estado: EstadoDocumentos, acoes: AcoesDocumentos, compartilhar: @Composable (Uuid) -> Unit, fuso: TimeZone) {
    val anulado = d.anuladoEm != null
    CardDieta(Modifier.fillMaxWidth().testTag("documento-${d.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            Text(d.titulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
            ChipStatus(if (anulado) EstiloStatus.ALERTA else EstiloStatus.FEITA, texto = stringResource(if (anulado) Res.string.doc_anulado else Res.string.doc_valido))
        }
        Text(stringResource(if (d.tipo == TipoDocumento.ATESTADO) Res.string.doc_emitido_em else Res.string.doc_emitida_em, dataDe(d.emitidoEm, fuso), d.codigoConferencia),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        d.motivoAnulacao?.takeIf { anulado }?.let { Text(stringResource(Res.string.doc_motivo_rotulo, it), style = MaterialTheme.typography.bodySmall) }
        if (!anulado) Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno), verticalAlignment = Alignment.CenterVertically) {
            compartilhar(d.id)
            val descricao = stringResource(Res.string.doc_anular_descricao, d.titulo, d.codigoConferencia)
            BotaoTexto(stringResource(Res.string.doc_anular), { acoes.aoAnular(d.id) }, Modifier.semantics { contentDescription = descricao },
                habilitado = !estado.semConexao && !estado.enviando)
        }
    }
}

/** MOB-94 (artboard `A-Documento-Emitir`): modelo, consulta (declaração) ou data e horário (atestado), e a prévia com os campos preenchidos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmitirDocumentoConteudo(estado: EstadoDocumentos, previa: List<TrechoPrevia>?, acoes: AcoesDocumentos, fuso: TimeZone = TimeZone.currentSystemDefault()) {
    val f = estado.emissao ?: return
    Column(Modifier.fillMaxWidth().padding(Dimens.paddingCard).testTag("emitir-documento"), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Text(stringResource(Res.string.doc_emitir_titulo), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.doc_para, estado.nomePaciente), style = MaterialTheme.typography.bodyMedium)
        Mensagens(estado.copy(aviso = null))
        Text(stringResource(Res.string.doc_modelo), style = MaterialTheme.typography.titleSmall)
        val modelos = estado.modelos
        if (modelos == null) { if (estado.erro == null) EstadoCarregando() }
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno), verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            modelos.forEach { m -> ChipEscolha(f.modeloId == m.id, { acoes.aoModelo(m.id) }, label = { Text(m.nome) }) }
        }
        if (ErroEmissao.MODELO in estado.errosEmissao) Text(stringResource(Res.string.doc_erro_modelo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (f.tipo == TipoDocumento.DECLARACAO_COMPARECIMENTO) {
            Text(stringResource(Res.string.doc_consulta), style = MaterialTheme.typography.titleSmall)
            val consultas = estado.consultasDoPaciente()
            if (consultas.isEmpty()) Text(stringResource(Res.string.doc_sem_consulta_realizada), style = MaterialTheme.typography.bodyMedium)
            consultas.take(6).forEach { c -> ChipEscolha(f.consultaId == c.id, { acoes.aoConsulta(c.id) }, label = { Text(rotuloConsulta(c, fuso)) }) }
            Text(stringResource(Res.string.doc_so_realizadas), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ErroEmissao.CONSULTA in estado.errosEmissao) Text(stringResource(Res.string.doc_erro_consulta), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else if (f.tipo != null) {
            CampoTexto(stringResource(Res.string.doc_data), f.data, acoes.aoData, Modifier.fillMaxWidth().testTag("documento-data"),
                erro = if (ErroEmissao.DATA in estado.errosEmissao) stringResource(Res.string.doc_erro_data) else null)
            CampoTexto(stringResource(Res.string.doc_horario), f.horario, acoes.aoHorario, Modifier.fillMaxWidth().testTag("documento-horario"),
                erro = if (ErroEmissao.HORARIO in estado.errosEmissao) stringResource(Res.string.doc_erro_horario) else null)
        }
        if (previa != null) CardDieta(Modifier.fillMaxWidth().testTag("documento-previa")) {
            Text(stringResource(Res.string.doc_previa), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            estado.modeloEscolhido()?.let { Text(it.nome, style = MaterialTheme.typography.titleSmall) }
            val cor = MaterialTheme.colorScheme.primary
            Text(buildAnnotatedString {
                previa.forEach { t -> if (t.campo) withStyle(SpanStyle(color = cor, fontWeight = FontWeight.SemiBold)) { append(t.texto) } else append(t.texto) }
            }, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.doc_campos_verdes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno, Alignment.End)) {
            BotaoTexto(stringResource(Res.string.doc_cancelar), acoes.aoFecharEmissao)
            BotaoPrincipal(stringResource(Res.string.doc_emitir_pdf), acoes.aoConfirmarEmissao, habilitado = !estado.semConexao, carregando = estado.enviando)
        }
    }
}

/** MOB-94 (artboard `A-Documento-Anular`): confirma com o motivo; não dá para desfazer. */
@Composable
fun AnularDocumentoConteudo(estado: EstadoDocumentos, acoes: AcoesDocumentos, fuso: TimeZone = TimeZone.currentSystemDefault()) {
    val d = estado.documentos.firstOrNull { it.id == estado.anulando } ?: return
    Column(Modifier.fillMaxWidth().padding(Dimens.paddingCard).testTag("anular-documento"), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Text(stringResource(Res.string.doc_anular_titulo, d.titulo.lowercase()), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.doc_anular_explica, d.titulo, dataDe(d.emitidoEm, fuso), d.codigoConferencia), style = MaterialTheme.typography.bodyMedium)
        Mensagens(estado.copy(aviso = null))
        CampoTexto(stringResource(Res.string.doc_motivo), estado.motivo, acoes.aoMotivo, Modifier.fillMaxWidth().testTag("documento-motivo"),
            erro = if (estado.erroMotivo) stringResource(Res.string.doc_erro_motivo) else null, singleLine = false)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno, Alignment.End)) {
            BotaoTexto(stringResource(Res.string.doc_voltar), acoes.aoDesistirAnulacao)
            BotaoPrincipal(stringResource(Res.string.doc_anular_confirmar), acoes.aoConfirmarAnulacao, habilitado = !estado.semConexao, carregando = estado.enviando)
        }
    }
}

@Composable
private fun Mensagens(estado: EstadoDocumentos) {
    estado.erro?.let { Text(stringResource(textoMensagem(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    estado.aviso?.let { Text(stringResource(textoMensagem(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
}

internal fun textoMensagem(m: MensagemDocumentos): StringResource = when (m) {
    MensagemDocumentos.SEM_CONEXAO -> Res.string.doc_msg_sem_conexao
    MensagemDocumentos.INVALIDO -> Res.string.doc_msg_invalido
    MensagemDocumentos.JA_ANULADO -> Res.string.doc_msg_ja_anulado
    MensagemDocumentos.ASSINATURA_INATIVA -> Res.string.doc_msg_assinatura
    MensagemDocumentos.VINCULO_ENCERRADO -> Res.string.doc_msg_vinculo
    MensagemDocumentos.SO_LEITURA -> Res.string.doc_msg_so_leitura
    MensagemDocumentos.CONFLITO -> Res.string.doc_msg_conflito
    MensagemDocumentos.ERRO -> Res.string.doc_msg_erro
    MensagemDocumentos.EMITIDO -> Res.string.doc_msg_emitido
    MensagemDocumentos.ANULADO -> Res.string.doc_msg_anulado
    MensagemDocumentos.MODELO_SALVO -> Res.string.doc_msg_modelo_salvo
    MensagemDocumentos.MODELO_EXCLUIDO -> Res.string.doc_msg_modelo_excluido
}

private fun primeiroNome(nome: String) = nome.trim().substringBefore(' ').ifBlank { nome }
private fun dataDe(i: Instant, fuso: TimeZone) = formatarData(i.toLocalDateTime(fuso).date)

@Composable
private fun rotuloConsulta(c: ConsultaResposta, fuso: TimeZone) =
    stringResource(Res.string.doc_consulta_rotulo, dataDe(c.inicio, fuso), formatarHora(c.inicio, fuso), formatarHora(c.fim, fuso))
