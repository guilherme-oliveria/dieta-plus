package org.br.dietamais.ui.nutri.paciente

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.saude.*
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.theme.AlertaClaro
import org.br.dietamais.ui.theme.AlertaTexto
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

data class AcoesSaudePaciente(val aoNovo: () -> Unit = {}, val aoEditar: (Uuid) -> Unit = {}, val aoCancelar: () -> Unit = {},
    val aoTipo: (TipoRestricaoSaude) -> Unit = {}, val aoNome: (String) -> Unit = {}, val aoGravidade: (GravidadeRestricao?) -> Unit = {},
    val aoObservacao: (String) -> Unit = {}, val aoMarcador: (String) -> Unit = {}, val aoSalvar: () -> Unit = {},
    val aoPedirRemocao: (Uuid) -> Unit = {}, val aoConfirmarRemocao: () -> Unit = {}, val aoCancelarRemocao: () -> Unit = {})

/** Selo do cabeçalho da ficha (`A-Saude-Ficha`): "Alergia grave: amendoim · +1". Some sem alergia nem intolerância. */
@Composable
fun SeloSaudeFicha(restricoes: List<RestricaoSaudeDto>?, modifier: Modifier = Modifier) {
    val selo = RestricoesSaude.selo(restricoes.orEmpty()) ?: return
    val p = selo.principal
    val tipo = stringResource(if (p.tipo == TipoRestricaoSaude.ALERGIA) Res.string.saude_tipo_alergia else Res.string.saude_tipo_intolerancia)
    val base = rotuloGravidade(p.gravidade)?.let { "$tipo ${it.lowercase()}" } ?: tipo
    val texto = "$base: ${p.nome.lowercase()}" + if (selo.outras > 0) " · +${selo.outras}" else ""
    Surface(color = AlertaClaro, shape = MaterialTheme.shapes.small, modifier = modifier.testTag("selo-saude")) {
        Text(texto, style = MaterialTheme.typography.labelLarge, color = AlertaTexto, modifier = Modifier.padding(horizontal = Dimens.espacoMedio, vertical = Dimens.espacoPequeno))
    }
}

/** Aba "Saúde" da ficha (MOB-82, artboards `A-Saude-Ficha` e `A-Saude-Item`, DES-16). */
@Composable
fun SaudePacienteTela(estado: EstadoSaudePaciente, acoes: AcoesSaudePaciente, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("saude-paciente"), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(Res.string.saude_titulo), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            if (estado.formulario == null && !estado.encerrado)
                BotaoContornado(stringResource(Res.string.saude_adicionar), acoes.aoNovo, Modifier.testTag("saude-adicionar"), habilitado = !estado.enviando)
        }
        estado.erro?.let { Text(textoMensagemSaude(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        estado.aviso?.let { Text(textoMensagemSaude(it), color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        val form = estado.formulario
        if (form != null) FormularioSaude(form, estado, acoes)
        else if (estado.itens.isEmpty()) EstadoVazio(mensagem = stringResource(Res.string.saude_vazio))
        else estado.itens.forEach { CartaoItemSaude(it, editavel = !estado.encerrado && !estado.enviando, acoes) }
        Text(stringResource(Res.string.saude_so_voce_ve), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    estado.removendo?.let { id ->
        val nome = estado.itens.firstOrNull { it.id == id }?.nome.orEmpty()
        AlertDialog(onDismissRequest = acoes.aoCancelarRemocao, modifier = Modifier.testTag("saude-remover-dialogo"),
            title = { Text(stringResource(Res.string.saude_remover_titulo)) }, text = { Text(stringResource(Res.string.saude_remover_texto, nome)) },
            confirmButton = { BotaoPrincipal(stringResource(Res.string.saude_remover), acoes.aoConfirmarRemocao, carregando = estado.enviando) },
            dismissButton = { BotaoTexto(stringResource(Res.string.saude_cancelar), acoes.aoCancelarRemocao) })
    }
}

@Composable
private fun CartaoItemSaude(r: RestricaoSaudeDto, editavel: Boolean, acoes: AcoesSaudePaciente) {
    CardDieta(Modifier.fillMaxWidth().testTag("saude-item-${r.id}")) {
        Text(rotuloTipo(r.tipo), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(r.nome, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
            rotuloGravidade(r.gravidade)?.let { g ->
                Surface(color = if (r.gravidade == GravidadeRestricao.GRAVE) AlertaClaro else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Text(g, style = MaterialTheme.typography.labelMedium, color = if (r.gravidade == GravidadeRestricao.GRAVE) AlertaTexto else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Dimens.espacoPequeno))
                }
            }
        }
        if (r.marcadores.isNotEmpty()) Text(stringResource(Res.string.saude_marca, r.marcadores.map { rotuloMarcador(it) }.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
        r.observacao?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (editavel) Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            BotaoTexto(stringResource(Res.string.saude_editar), { acoes.aoEditar(r.id) }, Modifier.testTag("saude-editar-${r.id}"))
            BotaoTexto(stringResource(Res.string.saude_remover), { acoes.aoPedirRemocao(r.id) }, Modifier.testTag("saude-remover-${r.id}"))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormularioSaude(f: FormularioItemSaude, estado: EstadoSaudePaciente, acoes: AcoesSaudePaciente) {
    val habilitado = !estado.enviando
    CardDieta(Modifier.fillMaxWidth().testTag("saude-formulario")) {
        Text(stringResource(if (f.id == null) Res.string.saude_novo_item else Res.string.saude_editar_item), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.saude_tipo), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            listOf(TipoRestricaoSaude.ALERGIA, TipoRestricaoSaude.INTOLERANCIA, TipoRestricaoSaude.CONDICAO, TipoRestricaoSaude.MEDICAMENTO).forEach { t ->
                ChipEscolha(f.tipo == t, { acoes.aoTipo(t) }, { Text(rotuloTipo(t)) }, Modifier.testTag("saude-tipo-${t.name}"), enabled = habilitado)
            }
        }
        CampoTexto(stringResource(Res.string.saude_nome), f.nome, acoes.aoNome, modifier = Modifier.testTag("saude-nome"), habilitado = habilitado,
            erro = when (estado.erroFormulario) { ErroItemSaude.NOME_OBRIGATORIO -> stringResource(Res.string.saude_erro_nome); ErroItemSaude.NOME_LONGO -> stringResource(Res.string.saude_erro_nome_longo); else -> null })
        Text(stringResource(Res.string.saude_gravidade), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            listOf(GravidadeRestricao.LEVE, GravidadeRestricao.MODERADA, GravidadeRestricao.GRAVE).forEach { g ->
                ChipEscolha(f.gravidade == g, { acoes.aoGravidade(g) }, { Text(rotuloGravidade(g).orEmpty()) }, Modifier.testTag("saude-gravidade-${g.name}"), enabled = habilitado)
            }
        }
        if (RestricoesSaude.ligaMarcadores(f.tipo)) {
            Text(stringResource(Res.string.saude_marcadores), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                RestricoesSaude.MARCADORES.forEach { m ->
                    ChipEscolha(m in f.marcadores, { acoes.aoMarcador(m) }, { Text(rotuloMarcador(m)) }, Modifier.testTag("saude-marcador-$m"), enabled = habilitado)
                }
            }
            Text(stringResource(Res.string.saude_marcadores_ajuda), style = MaterialTheme.typography.bodySmall)
        }
        CampoTexto(stringResource(Res.string.saude_observacao), f.observacao, acoes.aoObservacao, modifier = Modifier.testTag("saude-observacao"), habilitado = habilitado, singleLine = false,
            erro = if (estado.erroFormulario == ErroItemSaude.OBSERVACAO_LONGA) stringResource(Res.string.saude_erro_observacao) else null)
        if (estado.erroFormulario == ErroItemSaude.LIMITE_ITENS) Text(stringResource(Res.string.saude_erro_limite), color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            BotaoTexto(stringResource(Res.string.saude_cancelar), acoes.aoCancelar)
            BotaoPrincipal(stringResource(Res.string.saude_salvar), acoes.aoSalvar, Modifier.testTag("saude-salvar"), habilitado = habilitado, carregando = estado.enviando)
        }
    }
}

@Composable
private fun rotuloTipo(t: TipoRestricaoSaude): String = stringResource(when (t) {
    TipoRestricaoSaude.ALERGIA -> Res.string.saude_tipo_alergia
    TipoRestricaoSaude.INTOLERANCIA -> Res.string.saude_tipo_intolerancia
    TipoRestricaoSaude.CONDICAO -> Res.string.saude_tipo_condicao
    TipoRestricaoSaude.MEDICAMENTO -> Res.string.saude_tipo_medicamento
    TipoRestricaoSaude.OUTRO -> Res.string.saude_tipo_outro
})

@Composable
internal fun rotuloGravidade(g: GravidadeRestricao?): String? = when (g) {
    GravidadeRestricao.LEVE -> stringResource(Res.string.saude_gravidade_leve)
    GravidadeRestricao.MODERADA -> stringResource(Res.string.saude_gravidade_moderada)
    GravidadeRestricao.GRAVE -> stringResource(Res.string.saude_gravidade_grave)
    else -> null
}?.replaceFirstChar { it.uppercase() }

@Composable
internal fun rotuloMarcador(codigo: String): String = when (codigo) {
    "AMENDOIM" -> stringResource(Res.string.saude_marcador_amendoim)
    "CONTEM_LACTOSE" -> stringResource(Res.string.saude_marcador_lactose)
    "CONTEM_GLUTEN" -> stringResource(Res.string.saude_marcador_gluten)
    "OVO" -> stringResource(Res.string.saude_marcador_ovo)
    "FRUTOS_DO_MAR" -> stringResource(Res.string.saude_marcador_frutos_do_mar)
    "SOJA" -> stringResource(Res.string.saude_marcador_soja)
    else -> codigo
}

@Composable
fun textoMensagemSaude(m: MensagemSaude): String = stringResource(when (m) {
    MensagemSaude.SALVO -> Res.string.saude_msg_salvo
    MensagemSaude.REMOVIDO -> Res.string.saude_msg_removido
    MensagemSaude.SEM_CONEXAO -> Res.string.saude_msg_sem_conexao
    MensagemSaude.ITEM_INVALIDO -> Res.string.saude_msg_item_invalido
    MensagemSaude.ASSINATURA_INATIVA -> Res.string.saude_msg_assinatura
    MensagemSaude.CONFLITO -> Res.string.saude_msg_conflito
    MensagemSaude.ERRO -> Res.string.saude_msg_erro
})

/** Liga a aba ao ViewModel: a ficha que chega do cache (ou da API, na web) atualiza a seção. */
@Composable
fun SaudePacienteRota(vm: SaudePacienteViewModel, paciente: PacienteResposta) {
    androidx.compose.runtime.LaunchedEffect(paciente) { vm.abrir(paciente) }
    val estado by vm.estado.collectAsState()
    SaudePacienteTela(estado, AcoesSaudePaciente(vm::novoItem, vm::editar, vm::cancelar, vm::mudarTipo, vm::mudarNome, vm::mudarGravidade, vm::mudarObservacao,
        vm::alternarMarcador, vm::salvar, vm::pedirRemocao, vm::confirmarRemocao, vm::cancelarRemocao))
}
