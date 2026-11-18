package org.br.dietamais.ui.nutri.assinatura

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import org.br.dietamais.data.remoto.dto.AntecedenciaLembrete
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.comum.*
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource

data class AcoesLembreteConsultas(val aoAntecedencia: (AntecedenciaLembrete) -> Unit = {}, val aoExtraUmaHora: (Boolean) -> Unit = {},
    val aoSalvar: () -> Unit = {}, val aoRecarregar: () -> Unit = {})

/** "Lembrete das consultas" na conta da nutri (MOB-86, artboard `A-Conta-Lembrete`, DES-17). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LembreteConsultasCard(estado: EstadoLembreteConsultas, acoes: AcoesLembreteConsultas, modifier: Modifier = Modifier) {
    CardDieta(modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.lembrete_consultas_titulo), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        val habilitado = !estado.enviando && estado.carregado && !estado.semConexao
        Text(stringResource(Res.string.lembrete_consultas_antecedencia), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.espacoPequeno)) {
            listOf(AntecedenciaLembrete.DUAS_HORAS to Res.string.lembrete_consultas_duas_horas, AntecedenciaLembrete.UM_DIA to Res.string.lembrete_consultas_um_dia,
                AntecedenciaLembrete.DOIS_DIAS to Res.string.lembrete_consultas_dois_dias).forEach { (valor, rotulo) ->
                ChipEscolha(selected = valor == estado.antecedencia, onClick = { acoes.aoAntecedencia(valor) }, label = { Text(stringResource(rotulo)) }, enabled = habilitado,
                    modifier = Modifier.heightIn(min = Dimens.alvoToque).testTag("lembrete-${valor.name}"))
            }
        }
        Text(stringResource(Res.string.lembrete_consultas_padrao), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().heightIn(min = Dimens.alvoToque).testTag("lembrete-extra")
            .toggleable(estado.extraUmaHora, enabled = habilitado, role = Role.Switch, onValueChange = acoes.aoExtraUmaHora),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.lembrete_consultas_extra))
                Text(stringResource(Res.string.lembrete_consultas_extra_ajuda), style = MaterialTheme.typography.bodySmall)
            }
            Switch(estado.extraUmaHora, onCheckedChange = null, enabled = habilitado)
        }
        Text(stringResource(Res.string.lembrete_consultas_confirmacao), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(Res.string.lembrete_consultas_privacidade), style = MaterialTheme.typography.bodySmall)
        estado.erro?.let { Text(textoMensagemLembrete(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        estado.aviso?.let { Text(textoMensagemLembrete(it), color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (!estado.carregado && !estado.enviando) BotaoContornado(stringResource(Res.string.lembrete_consultas_tentar), acoes.aoRecarregar, Modifier.fillMaxWidth(), habilitado = !estado.semConexao)
        else BotaoPrincipal(stringResource(Res.string.lembrete_consultas_salvar), acoes.aoSalvar, Modifier.fillMaxWidth(), habilitado = habilitado && estado.alterado, carregando = estado.enviando)
    }
}

@Composable
fun textoMensagemLembrete(m: MensagemLembrete): String = stringResource(when (m) {
    MensagemLembrete.SEM_CONEXAO -> Res.string.lembrete_consultas_msg_sem_conexao
    MensagemLembrete.ASSINATURA_INATIVA -> Res.string.lembrete_consultas_msg_assinatura
    MensagemLembrete.ERRO -> Res.string.lembrete_consultas_msg_erro
    MensagemLembrete.SALVO -> Res.string.lembrete_consultas_msg_salvo
})
