package org.br.dietamais.ui.nutri.metas

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.metas.*
import org.br.dietamais.dominio.rotina.diaMes
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.theme.AlertaTexto
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

class AcoesMetas(val aoNova: () -> Unit = {}, val aoEditar: (Uuid) -> Unit = {}, val aoRecarregar: () -> Unit = {}, val aoAbrirModelos: () -> Unit = {},
    val aoTipo: (TipoMeta) -> Unit = {}, val aoNome: (String) -> Unit = {}, val aoAlvo: (String) -> Unit = {}, val aoUnidade: (String) -> Unit = {},
    val aoFrequencia: (FrequenciaMeta) -> Unit = {}, val aoVezes: (Int) -> Unit = {}, val aoInicio: (String) -> Unit = {}, val aoFim: (String) -> Unit = {},
    val aoSalvar: () -> Unit = {}, val aoEncerrar: () -> Unit = {}, val aoFecharFormulario: () -> Unit = {},
    val aoAplicar: (Uuid) -> Unit = {}, val aoExcluirModelo: (Uuid) -> Unit = {}, val aoNovoModelo: () -> Unit = {}, val aoNomeModelo: (String) -> Unit = {},
    val aoSalvarModelo: () -> Unit = {}, val aoFecharModelos: () -> Unit = {})

/** Liga a aba Metas da ficha ao ViewModel, com as folhas de nova meta e de modelos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetasPacienteRota(vm: MetasNutriViewModel, pacienteId: Uuid, nomePaciente: String) {
    val estado by vm.estado.collectAsState()
    LaunchedEffect(pacienteId) { vm.abrir(pacienteId) }
    val acoes = remember(vm) { AcoesMetas(vm::novaMeta, vm::editar, vm::recarregar, vm::abrirModelos, vm::mudarTipo, vm::mudarNome, vm::mudarAlvo, vm::mudarUnidade,
        vm::mudarFrequencia, vm::mudarVezes, vm::mudarInicio, vm::mudarFim, vm::salvarMeta, vm::encerrarMeta, vm::fecharFormulario,
        vm::aplicarModelo, vm::excluirModelo, vm::iniciarNovoModelo, vm::mudarNomeModelo, vm::salvarModelo, vm::fecharModelos) }
    MetasPacienteTela(estado, acoes)
    if (estado.formulario != null) ModalBottomSheet(onDismissRequest = vm::fecharFormulario) { FormularioMetaConteudo(estado, nomePaciente, acoes) }
    if (estado.modelosAbertos) ModalBottomSheet(onDismissRequest = vm::fecharModelos) { ModelosMetaConteudo(estado, nomePaciente, acoes) }
}

/** MOB-91 (artboard `A-Metas-Paciente`): metas em vigor com o cumprimento da semana; abaixo de 50% em laranja. */
@Composable
fun MetasPacienteTela(estado: EstadoMetasNutri, acoes: AcoesMetas, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Mensagens(estado)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            BotaoPrincipal(stringResource(Res.string.metas_nova), acoes.aoNova, Modifier.weight(1f), habilitado = !estado.semConexao && estado.carregado)
            BotaoContornado(stringResource(Res.string.metas_aplicar_modelo), acoes.aoAbrirModelos, Modifier.weight(1f), habilitado = !estado.semConexao && estado.carregado)
        }
        when {
            estado.carregando && !estado.carregado -> EstadoCarregando()
            !estado.carregado -> if (estado.erro != null) EstadoErro(acoes.aoRecarregar)
            else -> {
                val c = estado.cumprimento
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.metas_cumprimento), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    if (c != null) Text(stringResource(Res.string.metas_periodo, diaMes(c.de), diaMes(c.ate)), style = MaterialTheme.typography.bodySmall)
                }
                val metas = estado.ativas()
                if (metas.isEmpty()) EstadoVazio(mensagem = stringResource(Res.string.metas_vazio))
                metas.forEach { m -> CartaoMeta(m, estado.cumprimentoDe(m.id), { acoes.aoEditar(m.id) }, habilitado = !estado.semConexao) }
            }
        }
    }
}

@Composable
private fun Mensagens(estado: EstadoMetasNutri) {
    (estado.erro ?: estado.aviso)?.let { m ->
        Text(stringResource(textoMensagem(m)), style = MaterialTheme.typography.bodyMedium,
            color = if (estado.erro != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

private fun textoMensagem(m: MensagemMetas) = when (m) {
    MensagemMetas.SEM_CONEXAO -> Res.string.metas_msg_sem_conexao
    MensagemMetas.META_INVALIDA -> Res.string.metas_msg_invalida
    MensagemMetas.CONFLITO -> Res.string.metas_msg_conflito
    MensagemMetas.ASSINATURA_INATIVA -> Res.string.metas_msg_assinatura
    MensagemMetas.ERRO -> Res.string.metas_msg_erro
    MensagemMetas.META_SALVA -> Res.string.metas_msg_salva
    MensagemMetas.META_ENCERRADA -> Res.string.metas_msg_encerrada
    MensagemMetas.MODELO_APLICADO -> Res.string.metas_msg_modelo_aplicado
    MensagemMetas.MODELO_SALVO -> Res.string.metas_msg_modelo_salvo
    MensagemMetas.MODELO_EXCLUIDO -> Res.string.metas_msg_modelo_excluido
}

@Composable
private fun CartaoMeta(m: MetaResposta, c: CumprimentoMeta?, aoEditar: () -> Unit, habilitado: Boolean) {
    val titulo = tituloMeta(m.tipo, m.nome)
    val abaixo = abaixoDaMetade(c?.percentual)
    CardDieta(Modifier.fillMaxWidth().testTag("meta-${m.id}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(descricaoMeta(m.tipo, m.alvo, m.unidade, m.frequencia, m.vezesPorSemana, m.alvoPadrao == true), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(c?.percentual?.let { "${kotlin.math.round(it).toInt()}%" } ?: stringResource(Res.string.metas_sem_dado),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = if (abaixo) AlertaTexto else MaterialTheme.colorScheme.primary)
            val descricao = stringResource(Res.string.metas_editar_descricao, titulo)
            BotaoTexto(stringResource(Res.string.metas_editar), aoEditar, Modifier.semantics { contentDescription = descricao }, habilitado = habilitado)
        }
        if (m.tipo == TipoMeta.AGUA && m.alvoPadrao == true) Text(stringResource(Res.string.metas_agua_padrao_ajuda), style = MaterialTheme.typography.bodySmall)
        if (abaixo) Text(stringResource(Res.string.metas_abaixo_metade), style = MaterialTheme.typography.bodySmall, color = AlertaTexto)
        if (c != null && c.sequenciaAtual > 1) Text(stringResource(Res.string.metas_sequencia, c.sequenciaAtual), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun rotuloTipo(tipo: TipoMeta) = stringResource(when (tipo) {
    TipoMeta.AGUA -> Res.string.metas_tipo_agua
    TipoMeta.ATIVIDADE_MIN -> Res.string.metas_tipo_atividade
    TipoMeta.PASSOS -> Res.string.metas_tipo_passos
    TipoMeta.SONO_HORAS -> Res.string.metas_tipo_sono
    TipoMeta.FRUTAS_VERDURAS -> Res.string.metas_tipo_frutas
    TipoMeta.SIM_NAO -> Res.string.metas_tipo_sim_nao
    TipoMeta.CONTADOR -> Res.string.metas_tipo_contador
    TipoMeta.OUTRO -> Res.string.metas_tipo_outro
})

@Composable
internal fun tituloMeta(tipo: TipoMeta, nome: String?) = nome?.takeIf { tipoLivre(tipo) && it.isNotBlank() } ?: rotuloTipo(tipo)

@Composable
internal fun unidadeTipo(tipo: TipoMeta, unidade: String?): String = when (tipo) {
    TipoMeta.AGUA -> stringResource(Res.string.metas_un_ml)
    TipoMeta.ATIVIDADE_MIN -> stringResource(Res.string.metas_un_min)
    TipoMeta.PASSOS -> stringResource(Res.string.metas_un_passos)
    TipoMeta.SONO_HORAS -> stringResource(Res.string.metas_un_h)
    TipoMeta.FRUTAS_VERDURAS -> stringResource(Res.string.metas_un_porcoes)
    else -> unidade.orEmpty()
}

/** "30 min, 3 vezes por semana", "2,1 L, todo dia · padrão 35 ml/kg", "Sim ou não, todo dia". */
@Composable
internal fun descricaoMeta(tipo: TipoMeta, alvo: Double, unidade: String?, frequencia: FrequenciaMeta, vezes: Int?, padrao: Boolean): String {
    val valor = when (tipo) {
        TipoMeta.SIM_NAO -> stringResource(Res.string.metas_tipo_sim_nao)
        TipoMeta.AGUA -> stringResource(Res.string.metas_desc_agua, litros(alvo))
        else -> stringResource(Res.string.metas_desc_valor, textoAlvo(alvo), unidadeTipo(tipo, unidade)).trim()
    }
    val comFrequencia = if (frequencia == FrequenciaMeta.SEMANAL && vezes != null) stringResource(Res.string.metas_desc_semanal, valor, vezes)
        else stringResource(Res.string.metas_desc_diaria, valor)
    return if (tipo == TipoMeta.AGUA && padrao) stringResource(Res.string.metas_desc_padrao, comFrequencia) else comFrequencia
}

/** 2100 ml vira "2,1". */
internal fun litros(ml: Double): String { val d = kotlin.math.round(ml / 100).toLong(); return "${d / 10},${d % 10}" }

/** MOB-91 (artboard `A-Meta-Nova`): tipo, nome, alvo, frequência e período. Na água só o alvo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormularioMetaConteudo(estado: EstadoMetasNutri, nomePaciente: String, acoes: AcoesMetas) {
    val f = estado.formulario ?: return
    val erros = estado.errosFormulario
    Column(Modifier.fillMaxWidth().padding(Dimens.paddingCard).testTag("formulario-meta"), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Text(if (f.edicao) stringResource(Res.string.metas_form_titulo_editar) else stringResource(Res.string.metas_form_titulo_nova, nomePaciente),
            style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Mensagens(estado.copy(aviso = null))
        if (!f.edicao) {
            Text(stringResource(Res.string.metas_form_tipo), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno), verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                TIPOS_CRIAVEIS.forEach { t -> ChipEscolha(f.tipo == t, { acoes.aoTipo(t) }, label = { Text(rotuloTipo(t)) }) }
            }
        } else Text(tituloMeta(f.tipo, f.nome.takeIf { it.isNotBlank() }), style = MaterialTheme.typography.titleMedium)
        val agua = f.tipo == TipoMeta.AGUA
        if (tipoLivre(f.tipo)) CampoTexto(stringResource(Res.string.metas_form_nome), f.nome, acoes.aoNome, Modifier.fillMaxWidth().testTag("meta-nome"),
            erro = if (ErroMeta.NOME in erros) stringResource(Res.string.metas_erro_nome) else null)
        if (f.tipo == TipoMeta.SIM_NAO) Text(stringResource(Res.string.metas_form_sim_nao_ajuda), style = MaterialTheme.typography.bodySmall)
        else CampoTexto(stringResource(Res.string.metas_form_alvo), f.alvo, acoes.aoAlvo, Modifier.fillMaxWidth().testTag("meta-alvo"),
            erro = if (ErroMeta.ALVO in erros) stringResource(Res.string.metas_erro_alvo) else null,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            unidade = unidadeTipo(f.tipo, f.unidade).takeIf { it.isNotBlank() })
        if (f.tipo == TipoMeta.CONTADOR) CampoTexto(stringResource(Res.string.metas_form_unidade), f.unidade, acoes.aoUnidade, Modifier.fillMaxWidth())
        if (agua) Text(stringResource(Res.string.metas_form_agua_ajuda), style = MaterialTheme.typography.bodySmall)
        if (!agua) {
            Text(stringResource(Res.string.metas_form_frequencia), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                ChipEscolha(f.frequencia == FrequenciaMeta.DIARIA, { acoes.aoFrequencia(FrequenciaMeta.DIARIA) }, label = { Text(stringResource(Res.string.metas_form_todo_dia)) })
                ChipEscolha(f.frequencia == FrequenciaMeta.SEMANAL, { acoes.aoFrequencia(FrequenciaMeta.SEMANAL) }, label = { Text(stringResource(Res.string.metas_form_vezes)) })
            }
            if (f.frequencia == FrequenciaMeta.SEMANAL) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                val menos = stringResource(Res.string.metas_form_menos); val mais = stringResource(Res.string.metas_form_mais)
                BotaoContornado("−", { acoes.aoVezes(-1) }, Modifier.semantics { contentDescription = menos }.testTag("meta-vezes-menos"), habilitado = f.vezesPorSemana > 1)
                Text(stringResource(Res.string.metas_form_vezes_valor, f.vezesPorSemana), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                BotaoContornado("+", { acoes.aoVezes(1) }, Modifier.semantics { contentDescription = mais }.testTag("meta-vezes-mais"), habilitado = f.vezesPorSemana < 7)
            }
            CampoTexto(stringResource(Res.string.metas_form_inicio), f.inicio, acoes.aoInicio, Modifier.fillMaxWidth().testTag("meta-inicio"),
                erro = if (ErroMeta.INICIO in erros) stringResource(Res.string.metas_erro_inicio) else null)
            CampoTexto(stringResource(Res.string.metas_form_fim), f.fim, acoes.aoFim, Modifier.fillMaxWidth().testTag("meta-fim"),
                erro = if (ErroMeta.FIM in erros) stringResource(Res.string.metas_erro_fim) else null)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (f.edicao && !agua) BotaoTexto(stringResource(Res.string.metas_form_encerrar), acoes.aoEncerrar, habilitado = !estado.enviando && !estado.semConexao)
            Spacer(Modifier.weight(1f))
            BotaoTexto(stringResource(Res.string.metas_form_cancelar), acoes.aoFecharFormulario)
            BotaoPrincipal(stringResource(Res.string.metas_form_salvar), acoes.aoSalvar, habilitado = !estado.semConexao, carregando = estado.enviando)
        }
    }
}

/** MOB-91 (artboard `A-Modelos-Meta`): aplicar um modelo, salvar as metas do paciente como modelo e excluir modelo. */
@Composable
fun ModelosMetaConteudo(estado: EstadoMetasNutri, nomePaciente: String, acoes: AcoesMetas) {
    Column(Modifier.fillMaxWidth().padding(Dimens.paddingCard).testTag("modelos-meta"), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Text(stringResource(Res.string.metas_modelos_titulo), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.metas_modelos_aplicar_em, nomePaciente), style = MaterialTheme.typography.bodyMedium)
        Mensagens(estado)
        val modelos = estado.modelos
        when {
            modelos == null -> if (estado.erro == null) EstadoCarregando()
            modelos.isEmpty() -> Text(stringResource(Res.string.metas_modelos_vazio), style = MaterialTheme.typography.bodyMedium)
            else -> modelos.forEach { m ->
                CardDieta(Modifier.fillMaxWidth().testTag("modelo-${m.id}")) {
                    Text(m.nome, style = MaterialTheme.typography.titleSmall)
                    Text(m.itens.map { i -> "${tituloMeta(i.tipo, i.nome)} ${descricaoMeta(i.tipo, i.alvo, i.unidade, i.frequencia, i.vezesPorSemana, false)}" }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno, Alignment.End)) {
                        val excluir = stringResource(Res.string.metas_modelos_excluir_descricao, m.nome)
                        val aplicar = stringResource(Res.string.metas_modelos_aplicar_descricao, m.nome)
                        BotaoTexto(stringResource(Res.string.metas_modelos_excluir), { acoes.aoExcluirModelo(m.id) }, Modifier.semantics { contentDescription = excluir },
                            habilitado = !estado.enviando && !estado.semConexao)
                        BotaoPrincipal(stringResource(Res.string.metas_modelos_aplicar), { acoes.aoAplicar(m.id) }, Modifier.semantics { contentDescription = aplicar },
                            habilitado = !estado.enviando && !estado.semConexao)
                    }
                }
            }
        }
        Text(stringResource(Res.string.metas_modelos_nota), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        val nome = estado.nomeNovoModelo
        val temItens = itensDeModelo(estado.ativas()).isNotEmpty()
        if (nome == null) BotaoContornado(stringResource(Res.string.metas_modelos_salvar_como), acoes.aoNovoModelo, Modifier.fillMaxWidth(), habilitado = temItens && !estado.semConexao)
        else {
            CampoTexto(stringResource(Res.string.metas_modelos_nome), nome, acoes.aoNomeModelo, Modifier.fillMaxWidth().testTag("modelo-nome"))
            BotaoPrincipal(stringResource(Res.string.metas_modelos_salvar), acoes.aoSalvarModelo, Modifier.fillMaxWidth(),
                habilitado = nome.isNotBlank() && temItens && !estado.semConexao, carregando = estado.enviando)
        }
        if (!temItens) Text(stringResource(Res.string.metas_modelos_sem_metas), style = MaterialTheme.typography.bodySmall)
        BotaoTexto(stringResource(Res.string.metas_modelos_fechar), acoes.aoFecharModelos)
    }
}
