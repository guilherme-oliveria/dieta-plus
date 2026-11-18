package org.br.dietamais.ui.nutri.prescricoes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.agenda.formatarData
import org.br.dietamais.dominio.prescricoes.*
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AcoesPrescricoes(val aoSecao: (SecaoPrescricoes) -> Unit = {}, val aoRecarregar: () -> Unit = {},
    val aoPrescrever: () -> Unit = {}, val aoEditarSuplemento: (Uuid) -> Unit = {}, val aoEncerrados: () -> Unit = {},
    val aoOrigem: (OrigemSuplemento) -> Unit = {}, val aoBuscaProduto: (String) -> Unit = {}, val aoProduto: (ProdutoResposta) -> Unit = {},
    val aoNome: (String) -> Unit = {}, val aoDose: (String) -> Unit = {}, val aoForma: (String) -> Unit = {}, val aoFrequencia: (String) -> Unit = {},
    val aoHorarios: (String) -> Unit = {}, val aoInicio: (String) -> Unit = {}, val aoFim: (String) -> Unit = {},
    val aoSalvarSuplemento: () -> Unit = {}, val aoEncerrarSuplemento: () -> Unit = {}, val aoFecharSuplemento: () -> Unit = {},
    val aoNovaOrientacao: () -> Unit = {}, val aoTitulo: (String) -> Unit = {}, val aoTexto: (String) -> Unit = {}, val aoNegrito: () -> Unit = {},
    val aoLista: () -> Unit = {}, val aoSalvarModelo: () -> Unit = {}, val aoUsarModelo: () -> Unit = {}, val aoEscolherModelo: (Uuid) -> Unit = {},
    val aoExcluirModelo: (Uuid) -> Unit = {}, val aoFecharModelos: () -> Unit = {}, val aoEnviar: () -> Unit = {}, val aoFecharOrientacao: () -> Unit = {},
    val aoArquivar: (Uuid) -> Unit = {})

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescricoesPacienteRota(vm: PrescricoesNutriViewModel, pacienteId: Uuid, nomePaciente: String) {
    val estado by vm.estado.collectAsState()
    LaunchedEffect(pacienteId) { vm.abrir(pacienteId) }
    val acoes = remember(vm) { AcoesPrescricoes(vm::mudarSecao, vm::recarregar, vm::prescrever, vm::editarSuplemento, vm::alternarEncerrados,
        vm::mudarOrigem, vm::buscarProduto, vm::escolherProduto, vm::mudarNome, vm::mudarDose, vm::mudarForma, vm::mudarFrequencia,
        vm::mudarHorarios, vm::mudarInicio, vm::mudarFim, vm::salvarSuplemento, vm::encerrarSuplemento, vm::fecharSuplemento,
        vm::novaOrientacao, vm::mudarTitulo, vm::mudarTexto, vm::negrito, vm::itemLista, vm::alternarSalvarModelo, vm::usarModelo, vm::escolherModelo,
        vm::excluirModelo, vm::fecharModelos, vm::enviarOrientacao, vm::fecharOrientacao, vm::arquivarOrientacao) }
    PrescricoesPacienteTela(estado, acoes)
    if (estado.suplemento != null) ModalBottomSheet(onDismissRequest = vm::fecharSuplemento) { FormularioSuplementoConteudo(estado, acoes) }
    if (estado.orientacao != null) ModalBottomSheet(onDismissRequest = vm::fecharOrientacao) { FormularioOrientacaoConteudo(estado, nomePaciente, acoes) }
}

/** MOB-92 (artboard `A-Suplementos-Paciente`): aba Prescrições com Suplementos e Orientações. */
@Composable
fun PrescricoesPacienteTela(estado: EstadoPrescricoes, acoes: AcoesPrescricoes, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            ChipEscolha(estado.secao == SecaoPrescricoes.SUPLEMENTOS, { acoes.aoSecao(SecaoPrescricoes.SUPLEMENTOS) }, label = { Text(stringResource(Res.string.presc_suplementos)) })
            ChipEscolha(estado.secao == SecaoPrescricoes.ORIENTACOES, { acoes.aoSecao(SecaoPrescricoes.ORIENTACOES) }, label = { Text(stringResource(Res.string.presc_orientacoes)) })
        }
        Mensagem(estado)
        when {
            estado.carregando && !estado.carregado -> EstadoCarregando()
            !estado.carregado -> if (estado.erro != null) EstadoErro(acoes.aoRecarregar)
            estado.secao == SecaoPrescricoes.SUPLEMENTOS -> ListaSuplementos(estado, acoes)
            else -> ListaOrientacoes(estado, acoes)
        }
    }
}

@Composable
private fun Mensagem(estado: EstadoPrescricoes) {
    (estado.erro ?: estado.aviso)?.let { m ->
        Text(stringResource(when (m) {
            MensagemPrescricoes.SEM_CONEXAO -> Res.string.presc_msg_sem_conexao
            MensagemPrescricoes.INVALIDO -> Res.string.presc_msg_invalido
            MensagemPrescricoes.CONFLITO -> Res.string.presc_msg_conflito
            MensagemPrescricoes.ASSINATURA_INATIVA -> Res.string.presc_msg_assinatura
            MensagemPrescricoes.VINCULO_ENCERRADO -> Res.string.presc_msg_vinculo
            MensagemPrescricoes.ERRO -> Res.string.presc_msg_erro
            MensagemPrescricoes.SUPLEMENTO_SALVO -> Res.string.presc_msg_salvo
            MensagemPrescricoes.SUPLEMENTO_ENCERRADO -> Res.string.presc_msg_encerrado
            MensagemPrescricoes.ORIENTACAO_ENVIADA -> Res.string.presc_msg_enviada
            MensagemPrescricoes.ORIENTACAO_ARQUIVADA -> Res.string.presc_msg_arquivada
            MensagemPrescricoes.MODELO_EXCLUIDO -> Res.string.presc_msg_modelo_excluido
        }), style = MaterialTheme.typography.bodyMedium,
            color = if (estado.erro != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun ListaSuplementos(estado: EstadoPrescricoes, acoes: AcoesPrescricoes) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.presc_em_uso), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        BotaoPrincipal(stringResource(Res.string.presc_prescrever), acoes.aoPrescrever, habilitado = !estado.semConexao)
    }
    val emUso = estado.emUso()
    if (emUso.isEmpty()) Text(stringResource(Res.string.presc_sem_suplementos), style = MaterialTheme.typography.bodyMedium)
    emUso.forEach { CartaoSuplemento(it, { acoes.aoEditarSuplemento(it.id) }, !estado.semConexao) }
    val encerrados = estado.encerrados()
    if (encerrados.isNotEmpty()) {
        BotaoTexto(stringResource(Res.string.presc_encerrados, encerrados.size), acoes.aoEncerrados)
        if (estado.mostrarEncerrados) encerrados.forEach { CartaoSuplemento(it, null, false) }
    }
    Text(stringResource(Res.string.presc_nota_pdf), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CartaoSuplemento(s: SuplementoResposta, aoEditar: (() -> Unit)?, habilitado: Boolean) {
    CardDieta(Modifier.fillMaxWidth().testTag("suplemento-${s.id}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.nome, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(if (s.produtoId != null) Res.string.presc_meu_produto else Res.string.presc_texto_livre), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(s.forma?.let { stringResource(Res.string.presc_dose_forma, s.dose, it) } ?: s.dose, style = MaterialTheme.typography.bodyMedium)
                val freq = s.frequencia.orEmpty(); val horas = s.horarios.joinToString(", ")
                val linha = when { freq.isNotBlank() && horas.isNotBlank() -> stringResource(Res.string.presc_frequencia_horarios, freq, horas); freq.isNotBlank() -> freq; else -> horas }
                if (linha.isNotBlank()) Text(linha, style = MaterialTheme.typography.bodySmall)
                Text(s.fim?.let { stringResource(Res.string.presc_periodo, formatarData(s.inicio), formatarData(it)) } ?: stringResource(Res.string.presc_desde, formatarData(s.inicio)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (aoEditar != null) {
                val d = stringResource(Res.string.presc_editar_descricao, s.nome)
                BotaoTexto(stringResource(Res.string.presc_editar), aoEditar, Modifier.semantics { contentDescription = d }, habilitado = habilitado)
            }
        }
    }
}

@Composable
private fun ListaOrientacoes(estado: EstadoPrescricoes, acoes: AcoesPrescricoes) {
    BotaoPrincipal(stringResource(Res.string.ori_nova), acoes.aoNovaOrientacao, Modifier.fillMaxWidth(), habilitado = !estado.semConexao)
    if (estado.orientacoes.isEmpty()) Text(stringResource(Res.string.ori_vazio), style = MaterialTheme.typography.bodyMedium)
    estado.orientacoes.forEach { o ->
        CardDieta(Modifier.fillMaxWidth().testTag("orientacao-${o.id}")) {
            Text(o.titulo, style = MaterialTheme.typography.titleSmall)
            Text(o.texto.lineSequence().take(3).joinToString("\n"), style = MaterialTheme.typography.bodySmall, maxLines = 3)
            Text(if (o.arquivadaEm != null) stringResource(Res.string.ori_arquivada) else stringResource(Res.string.ori_enviada_em, dataDe(o.criadoEm)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (o.arquivadaEm == null) {
                val d = stringResource(Res.string.ori_arquivar_descricao, o.titulo)
                BotaoTexto(stringResource(Res.string.ori_arquivar), { acoes.aoArquivar(o.id) }, Modifier.semantics { contentDescription = d },
                    habilitado = !estado.semConexao && !estado.enviando)
            }
        }
    }
}

private fun dataDe(i: Instant) = formatarData(i.toLocalDateTime(TimeZone.currentSystemDefault()).date)

/** MOB-92 (artboard `A-Suplemento-Novo`): produto da nutri ou texto livre, dose, horário, forma, frequência e período. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormularioSuplementoConteudo(estado: EstadoPrescricoes, acoes: AcoesPrescricoes) {
    val f = estado.suplemento ?: return
    val erros = estado.errosSuplemento
    Column(Modifier.fillMaxWidth().padding(Dimens.paddingCard).testTag("formulario-suplemento"), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Text(stringResource(if (f.edicao) Res.string.presc_form_editar else Res.string.presc_form_titulo), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        Mensagem(estado.copy(aviso = null))
        if (f.edicao) {
            Text(f.nome, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.presc_form_congelado), style = MaterialTheme.typography.bodySmall)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
                ChipEscolha(f.origem == OrigemSuplemento.PRODUTO, { acoes.aoOrigem(OrigemSuplemento.PRODUTO) }, label = { Text(stringResource(Res.string.presc_meu_produto)) })
                ChipEscolha(f.origem == OrigemSuplemento.TEXTO_LIVRE, { acoes.aoOrigem(OrigemSuplemento.TEXTO_LIVRE) }, label = { Text(stringResource(Res.string.presc_texto_livre)) })
            }
            if (f.origem == OrigemSuplemento.PRODUTO) {
                if (f.produtoId != null) Text(stringResource(Res.string.presc_form_produto) + ": " + f.produtoNome, style = MaterialTheme.typography.bodyLarge)
                CampoTexto(stringResource(Res.string.presc_form_buscar), estado.buscaProduto, acoes.aoBuscaProduto, Modifier.fillMaxWidth().testTag("suplemento-busca"),
                    erro = if (ErroSuplemento.PRODUTO in erros) stringResource(Res.string.presc_erro_produto) else null)
                estado.produtos.take(6).forEach { p -> BotaoTexto(listOfNotNull(p.nome, p.marca).joinToString(" · "), { acoes.aoProduto(p) }) }
                Text(stringResource(Res.string.presc_form_produto_ajuda), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else CampoTexto(stringResource(Res.string.presc_form_nome), f.nome, acoes.aoNome, Modifier.fillMaxWidth().testTag("suplemento-nome"),
                erro = if (ErroSuplemento.NOME in erros) stringResource(Res.string.presc_erro_nome) else null)
        }
        CampoTexto(stringResource(Res.string.presc_form_dose), f.dose, acoes.aoDose, Modifier.fillMaxWidth().testTag("suplemento-dose"),
            erro = if (ErroSuplemento.DOSE in erros) stringResource(Res.string.presc_erro_dose) else null)
        CampoTexto(stringResource(Res.string.presc_form_horarios), f.horarios, acoes.aoHorarios, Modifier.fillMaxWidth().testTag("suplemento-horarios"),
            erro = if (ErroSuplemento.HORARIOS in erros) stringResource(Res.string.presc_erro_horarios) else null)
        Text(stringResource(Res.string.presc_form_forma), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno), verticalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            FORMAS_SUPLEMENTO.forEach { forma -> ChipEscolha(f.forma == forma, { acoes.aoForma(forma) }, label = { Text(forma) }) }
        }
        CampoTexto(stringResource(Res.string.presc_form_frequencia), f.frequencia, acoes.aoFrequencia, Modifier.fillMaxWidth())
        if (!f.edicao) CampoTexto(stringResource(Res.string.presc_form_inicio), f.inicio, acoes.aoInicio, Modifier.fillMaxWidth(),
            erro = if (ErroSuplemento.INICIO in erros) stringResource(Res.string.presc_erro_inicio) else null)
        CampoTexto(stringResource(Res.string.presc_form_fim), f.fim, acoes.aoFim, Modifier.fillMaxWidth().testTag("suplemento-fim"),
            erro = if (ErroSuplemento.FIM in erros) stringResource(Res.string.presc_erro_fim) else null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno), verticalAlignment = Alignment.CenterVertically) {
            if (f.edicao) BotaoTexto(stringResource(Res.string.presc_form_encerrar), acoes.aoEncerrarSuplemento, habilitado = !estado.enviando && !estado.semConexao)
            Spacer(Modifier.weight(1f))
            BotaoTexto(stringResource(Res.string.presc_form_cancelar), acoes.aoFecharSuplemento)
            BotaoPrincipal(stringResource(if (f.edicao) Res.string.presc_form_salvar_edicao else Res.string.presc_form_salvar), acoes.aoSalvarSuplemento,
                habilitado = !estado.semConexao, carregando = estado.enviando)
        }
    }
}

/** MOB-92 (artboard `A-Orientacao-Nova`): título, texto com negrito e lista, usar modelo e salvar como modelo. */
@Composable
fun FormularioOrientacaoConteudo(estado: EstadoPrescricoes, nomePaciente: String, acoes: AcoesPrescricoes) {
    val f = estado.orientacao ?: return
    Column(Modifier.fillMaxWidth().padding(Dimens.paddingCard).testTag("formulario-orientacao"), verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Text(stringResource(Res.string.ori_nova), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.ori_para, nomePaciente), style = MaterialTheme.typography.bodyMedium)
        Mensagem(estado.copy(aviso = if (estado.aviso == MensagemPrescricoes.MODELO_EXCLUIDO) estado.aviso else null))
        if (estado.escolhendoModelo) { ListaModelos(estado, acoes); return@Column }
        CampoTexto(stringResource(Res.string.ori_titulo), f.titulo, acoes.aoTitulo, Modifier.fillMaxWidth().testTag("orientacao-titulo"),
            erro = if (ErroOrientacao.TITULO in estado.errosOrientacao) stringResource(Res.string.ori_erro_titulo) else null)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno), verticalAlignment = Alignment.CenterVertically) {
            val negrito = stringResource(Res.string.ori_negrito); val lista = stringResource(Res.string.ori_lista)
            BotaoContornado("B", acoes.aoNegrito, Modifier.semantics { contentDescription = negrito })
            BotaoContornado("•≡", acoes.aoLista, Modifier.semantics { contentDescription = lista })
            Spacer(Modifier.weight(1f))
            BotaoTexto(stringResource(Res.string.ori_usar_modelo), acoes.aoUsarModelo, habilitado = !estado.semConexao)
        }
        CampoTexto(stringResource(Res.string.ori_texto), f.texto, acoes.aoTexto, Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("orientacao-texto"),
            erro = if (ErroOrientacao.TEXTO in estado.errosOrientacao) stringResource(Res.string.ori_erro_texto) else null, singleLine = false)
        Text(stringResource(Res.string.ori_ajuda), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().toggleable(f.salvarComoModelo, role = Role.Checkbox, onValueChange = { acoes.aoSalvarModelo() }),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            Checkbox(f.salvarComoModelo, null)
            Column {
                Text(stringResource(Res.string.ori_salvar_modelo), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.ori_salvar_modelo_ajuda), style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno, Alignment.End)) {
            BotaoTexto(stringResource(Res.string.ori_cancelar), acoes.aoFecharOrientacao)
            BotaoPrincipal(stringResource(Res.string.ori_enviar), acoes.aoEnviar, habilitado = !estado.semConexao, carregando = estado.enviando)
        }
    }
}

@Composable
private fun ListaModelos(estado: EstadoPrescricoes, acoes: AcoesPrescricoes) {
    Text(stringResource(Res.string.ori_modelos_titulo), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    val modelos = estado.modelos
    when {
        modelos == null -> if (estado.erro == null) EstadoCarregando()
        modelos.isEmpty() -> Text(stringResource(Res.string.ori_modelos_vazio), style = MaterialTheme.typography.bodyMedium)
        else -> modelos.forEach { m ->
            CardDieta(Modifier.fillMaxWidth().testTag("modelo-orientacao-${m.id}")) {
                Text(m.titulo, style = MaterialTheme.typography.titleSmall)
                Text(m.texto, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno, Alignment.End)) {
                    val excluir = stringResource(Res.string.ori_modelo_excluir_descricao, m.titulo); val usar = stringResource(Res.string.ori_modelo_usar_descricao, m.titulo)
                    BotaoTexto(stringResource(Res.string.ori_modelo_excluir), { acoes.aoExcluirModelo(m.id) }, Modifier.semantics { contentDescription = excluir },
                        habilitado = !estado.enviando && !estado.semConexao)
                    BotaoPrincipal(stringResource(Res.string.ori_modelo_usar), { acoes.aoEscolherModelo(m.id) }, Modifier.semantics { contentDescription = usar })
                }
            }
        }
    }
    BotaoTexto(stringResource(Res.string.ori_modelos_voltar), acoes.aoFecharModelos)
}
