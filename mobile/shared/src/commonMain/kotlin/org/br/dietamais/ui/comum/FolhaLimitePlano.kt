package org.br.dietamais.ui.comum

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import org.br.dietamais.data.remoto.dto.AssinaturaResumoResposta
import org.br.dietamais.recursos.*
import org.br.dietamais.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource

@Composable
fun FolhaLimitePlano(assinatura: AssinaturaResumoResposta?, aoFechar: () -> Unit) = AlertDialog(
    onDismissRequest = aoFechar,
    title = { Text(stringResource(Res.string.pacientes_limite_titulo)) },
    text = { Column(verticalArrangement = Arrangement.spacedBy(Dimens.espacoMedio)) {
        Text(stringResource(Res.string.pacientes_limite_texto))
        assinatura?.let { Text(textoUsoPlano(it)) }
    } }, confirmButton = { BotaoTexto(stringResource(Res.string.pacientes_entendi), aoFechar) })

/** "N pacientes ocupados de M", ou "plano sem limite" quando o limite é nulo (BE-80). */
@Composable
fun textoUsoPlano(a: AssinaturaResumoResposta): String =
    a.limitePacientes?.let { stringResource(Res.string.pacientes_uso_plano, a.pacientesOcupados, it) } ?: stringResource(Res.string.pacientes_uso_plano_sem_limite, a.pacientesOcupados)
