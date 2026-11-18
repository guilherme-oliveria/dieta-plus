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
import androidx.compose.ui.unit.dp
import org.br.dietamais.data.remoto.dto.ModeloDocumentoResposta
import org.br.dietamais.data.remoto.dto.TipoDocumento
import org.br.dietamais.dominio.documentos.CAMPOS_DOCUMENTO
import org.br.dietamais.dominio.documentos.ErroModeloDoc
import org.br.dietamais.dominio.documentos.camposDoTexto
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

class AcoesModelosDocumento(val aoRecarregar: () -> Unit = {}, val aoAbrir: (Uuid) -> Unit = {}, val aoNovo: () -> Unit = {}, val aoTipo: (TipoDocumento) -> Unit = {},
    val aoNome: (String) -> Unit = {}, val aoTexto: (String) -> Unit = {}, val aoCampo: (String) -> Unit = {}, val aoSalvar: () -> Unit = {}, val aoFechar: () -> Unit = {},
    val aoExcluir: (Uuid) -> Unit = {}, val aoConfirmarExclusao: () -> Unit = {}, val aoDesistirExclusao: () -> Unit = {})

/** Cartão "Modelos de documento" na Conta da nutri, com a folha de edição (MOB-94). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelosDocumentoRota(vm: ModelosDocumentoViewModel) {
    val estado by vm.estado.collectAsState()
    LaunchedEffect(vm) { if (estado.modelos == null) vm.recarregar() }
    val acoes = remember(vm) { AcoesModelosDocumento(vm::recarregar, vm::abrir, vm::novo, vm::mudarTipo, vm::mudarNome, vm::mudarTexto, vm::inserirCampo, vm::salvar,
        vm::fechar, vm::pedirExclusao, vm::excluir, vm::desistirDaExclusao) }
    ModelosDocumentoCard(estado, acoes)
    if (estado.formulario != null) ModalBottomSheet(onDismissRequest = vm::fechar) { FormularioModeloDocConteudo(estado, acoes) }
}

@Composable
fun ModelosDocumentoCard(estado: EstadoModelosDocumento, acoes: AcoesModelosDocumento) {
    CardDieta(Modifier.fillMaxWidth().testTag("modelos-documento")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.doc_modelos_titulo), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            BotaoTexto(stringResource(Res.string.doc_modelo_novo), acoes.aoNovo, habilitado = !estado.semConexao && estado.modelos != null)
        }
        estado.erro?.takeIf { estado.formulario == null }?.let { Text(stringResource(textoMensagem(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        estado.aviso?.let { Text(stringResource(textoMensagem(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        val modelos = estado.modelos
        when {
            modelos == null && estado.carregando -> EstadoCarregando()
            modelos == null -> BotaoTexto(stringResource(Res.string.doc_tentar), acoes.aoRecarregar)
            else -> modelos.forEach { m -> LinhaModelo(m, estado, acoes) }
        }
        Text(stringResource(Res.string.doc_modelos_nota), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LinhaModelo(m: ModeloDocumentoResposta, estado: EstadoModelosDocumento, acoes: AcoesModelosDocumento) {
    val habilitado = !estado.semConexao && !estado.enviando
    Column(Modifier.fillMaxWidth().padding(vertical = Dimens.espacoPequeno).testTag("modelo-documento-${m.id}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(m.nome, style = MaterialTheme.typography.titleSmall)
        Text(stringResource(if (m.doSistema) Res.string.doc_modelo_sistema else Res.string.doc_modelo_meu), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary)
        val campos = camposDoTexto(m.texto)
        if (campos.isNotEmpty()) Text(stringResource(Res.string.doc_campos, campos.map { rotuloCampoTexto(it) }.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
        if (estado.excluindo == m.id) {
            Text(stringResource(Res.string.doc_modelo_excluir_pergunta, m.nome), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                BotaoTexto(stringResource(Res.string.doc_voltar), acoes.aoDesistirExclusao)
                BotaoPrincipal(stringResource(Res.string.doc_modelo_excluir), acoes.aoConfirmarExclusao, habilitado = habilitado, carregando = estado.enviando)
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            if (m.doSistema) BotaoContornado(stringResource(Res.string.doc_modelo_copiar), { acoes.aoAbrir(m.id) }, habilitado = habilitado)
            else {
                val editar = stringResource(Res.string.doc_modelo_editar_descricao, m.nome); val excluir = stringResource(Res.string.doc_modelo_excluir_descricao, m.nome)
                BotaoContornado(stringResource(Res.string.doc_modelo_editar), { acoes.aoAbrir(m.id) }, Modifier.semantics { contentDescription = editar }, habilitado = habilitado)
                BotaoTexto(stringResource(Res.string.doc_modelo_excluir), { acoes.aoExcluir(m.id) }, Modifier.semantics { contentDescription = excluir }, habilitado = habilitado)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormularioModeloDocConteudo(estado: EstadoModelosDocumento, acoes: AcoesModelosDocumento) {
    val f = estado.formulario ?: return
    Column(Modifier.fillMaxWidth().padding(Dimens.paddingCard).testTag("formulario-modelo-documento"), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Text(stringResource(if (f.edicao) Res.string.doc_modelo_editar_titulo else Res.string.doc_modelo_novo_titulo), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        estado.erro?.let { Text(stringResource(textoMensagem(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            listOf(TipoDocumento.DECLARACAO_COMPARECIMENTO, TipoDocumento.ATESTADO).forEach { t ->
                ChipEscolha(f.tipo == t, { acoes.aoTipo(t) }, label = { Text(stringResource(if (t == TipoDocumento.ATESTADO) Res.string.doc_tipo_atestado else Res.string.doc_tipo_declaracao)) })
            }
        }
        CampoTexto(stringResource(Res.string.doc_modelo_nome), f.nome, acoes.aoNome, Modifier.fillMaxWidth().testTag("modelo-documento-nome"),
            erro = if (ErroModeloDoc.NOME in estado.erros) stringResource(Res.string.doc_erro_nome) else null)
        Text(stringResource(Res.string.doc_inserir_campo), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            CAMPOS_DOCUMENTO.forEach { c -> BotaoTexto(rotuloCampoTexto(c), { acoes.aoCampo(c) }) }
        }
        CampoTexto(stringResource(Res.string.doc_modelo_texto), f.texto, acoes.aoTexto, Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("modelo-documento-texto"),
            erro = if (ErroModeloDoc.TEXTO in estado.erros) stringResource(Res.string.doc_erro_texto) else null, singleLine = false)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno, Alignment.End)) {
            BotaoTexto(stringResource(Res.string.doc_cancelar), acoes.aoFechar)
            BotaoPrincipal(stringResource(Res.string.doc_modelo_salvar), acoes.aoSalvar, habilitado = !estado.semConexao, carregando = estado.enviando)
        }
    }
}

@Composable
private fun rotuloCampoTexto(campo: String) = stringResource(rotuloCampo(campo))

private fun rotuloCampo(campo: String): StringResource = when (campo) {
    "paciente" -> Res.string.doc_campo_paciente
    "data" -> Res.string.doc_campo_data
    "horario" -> Res.string.doc_campo_horario
    "nutri" -> Res.string.doc_campo_nutri
    else -> Res.string.doc_campo_crn
}
