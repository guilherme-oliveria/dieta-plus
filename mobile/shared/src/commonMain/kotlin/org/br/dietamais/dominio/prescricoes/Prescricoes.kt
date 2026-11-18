package org.br.dietamais.dominio.prescricoes

import kotlinx.datetime.LocalDate
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.agenda.formatarData
import org.br.dietamais.dominio.agenda.lerData
import org.br.dietamais.dominio.agenda.lerHora
import kotlin.uuid.Uuid

// Limites do contrato (BE-75, BE-76); o backend decide.
const val LIMITE_NOME_SUPLEMENTO = 120
const val LIMITE_DOSE = 80
const val LIMITE_FORMA = 60
const val LIMITE_FREQUENCIA = 120
const val MAX_HORARIOS = 6
const val LIMITE_TITULO_ORIENTACAO = 120
const val LIMITE_TEXTO_ORIENTACAO = 5000

/** Formas sugeridas no artboard; a nutri pode deixar sem forma. */
val FORMAS_SUPLEMENTO = listOf("Cápsula", "Pó", "Comprimido", "Gotas", "Líquido")

enum class OrigemSuplemento { PRODUTO, TEXTO_LIVRE }
enum class ErroSuplemento { PRODUTO, NOME, DOSE, HORARIOS, INICIO, FIM }

/** MOB-92 (RN-SUP-01): na edição só dose, forma, frequência, horários e fim mudam (nome e produto ficam congelados, RN-SUP-02). */
data class FormularioSuplemento(val origem: OrigemSuplemento = OrigemSuplemento.PRODUTO, val produtoId: Uuid? = null, val produtoNome: String = "",
    val nome: String = "", val dose: String = "", val forma: String = "", val frequencia: String = "", val horarios: String = "",
    val inicio: String = "", val fim: String = "", val suplementoId: Uuid? = null, val versao: Long? = null) {
    val edicao get() = suplementoId != null
}

fun novoFormularioSuplemento(hoje: LocalDate, frequenciaPadrao: String) = FormularioSuplemento(frequencia = frequenciaPadrao, inicio = formatarData(hoje))

fun formularioDe(s: SuplementoResposta) = FormularioSuplemento(if (s.produtoId != null) OrigemSuplemento.PRODUTO else OrigemSuplemento.TEXTO_LIVRE,
    s.produtoId, s.nome, s.nome, s.dose, s.forma.orEmpty(), s.frequencia.orEmpty(), s.horarios.joinToString(", "),
    formatarData(s.inicio), s.fim?.let(::formatarData).orEmpty(), s.id, s.versao)

/** "8:00, 20:00" vira ["08:00", "20:00"], sem repetir e em ordem; null se algum não for hora válida ou passar de 6. */
fun lerHorarios(texto: String): List<String>? {
    val partes = texto.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
    val horas = partes.map { p -> lerHora(p)?.let { (h, m) -> "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}" } ?: return null }
    return horas.distinct().sorted().takeIf { it.size <= MAX_HORARIOS }
}

fun validarSuplemento(f: FormularioSuplemento): Set<ErroSuplemento> {
    val erros = mutableSetOf<ErroSuplemento>()
    if (!f.edicao) {
        if (f.origem == OrigemSuplemento.PRODUTO && f.produtoId == null) erros += ErroSuplemento.PRODUTO
        if (f.origem == OrigemSuplemento.TEXTO_LIVRE && (f.nome.isBlank() || f.nome.trim().length > LIMITE_NOME_SUPLEMENTO)) erros += ErroSuplemento.NOME
        if (lerData(f.inicio) == null) erros += ErroSuplemento.INICIO
    }
    if (f.dose.isBlank() || f.dose.trim().length > LIMITE_DOSE) erros += ErroSuplemento.DOSE
    if (lerHorarios(f.horarios) == null) erros += ErroSuplemento.HORARIOS
    val fim = f.fim.takeIf { it.isNotBlank() }?.let(::lerData)
    if (f.fim.isNotBlank() && fim == null) erros += ErroSuplemento.FIM
    val inicio = lerData(f.inicio)
    if (fim != null && inicio != null && fim < inicio) erros += ErroSuplemento.FIM
    return erros
}

private fun opcional(texto: String, limite: Int) = texto.trim().take(limite).takeIf { it.isNotEmpty() }

/** Produto ou nome, nunca os dois (RN-SUP-01). */
fun requisicaoSuplemento(f: FormularioSuplemento, id: Uuid) = SuplementoRequisicao(id,
    f.produtoId.takeIf { f.origem == OrigemSuplemento.PRODUTO },
    f.nome.trim().takeIf { f.origem == OrigemSuplemento.TEXTO_LIVRE },
    f.dose.trim(), opcional(f.forma, LIMITE_FORMA), opcional(f.frequencia, LIMITE_FREQUENCIA),
    lerHorarios(f.horarios).orEmpty(), lerData(f.inicio), f.fim.takeIf { it.isNotBlank() }?.let(::lerData))

fun atualizacaoSuplemento(f: FormularioSuplemento) = AtualizarSuplementoRequisicao(f.dose.trim(), opcional(f.forma, LIMITE_FORMA),
    opcional(f.frequencia, LIMITE_FREQUENCIA), lerHorarios(f.horarios).orEmpty(), f.fim.takeIf { it.isNotBlank() }?.let(::lerData))

/** Em uso: sem fim ou com fim de hoje em diante. Mais novo primeiro. */
fun suplementosEmUso(lista: List<SuplementoResposta>, hoje: LocalDate) = lista.filter { it.fim == null || it.fim >= hoje }.sortedByDescending { it.inicio }
fun suplementosEncerrados(lista: List<SuplementoResposta>, hoje: LocalDate) = lista.filter { it.fim != null && it.fim < hoje }.sortedByDescending { it.fim }

// ---- orientações (RN-ORI-01)
enum class ErroOrientacao { TITULO, TEXTO }

data class FormularioOrientacao(val titulo: String = "", val texto: String = "", val salvarComoModelo: Boolean = false)

fun validarOrientacao(f: FormularioOrientacao): Set<ErroOrientacao> {
    val erros = mutableSetOf<ErroOrientacao>()
    if (f.titulo.isBlank() || f.titulo.trim().length > LIMITE_TITULO_ORIENTACAO) erros += ErroOrientacao.TITULO
    if (f.texto.isBlank() || f.texto.trim().length > LIMITE_TEXTO_ORIENTACAO) erros += ErroOrientacao.TEXTO
    return erros
}

/** Botão "B": acrescenta um trecho em negrito no fim do texto (o campo é texto simples; o paciente vê formatado). */
fun acrescentarNegrito(texto: String) = texto + (if (texto.isEmpty() || texto.endsWith(' ') || texto.endsWith('\n')) "" else " ") + "**negrito**"

/** Botão de lista: começa um item "- " numa linha nova. */
fun acrescentarItemLista(texto: String) = texto + (if (texto.isEmpty() || texto.endsWith('\n')) "" else "\n") + "- "

/** Orientações visíveis ao paciente primeiro (não arquivadas), mais novas antes. */
fun orientacoesOrdenadas(lista: List<OrientacaoResposta>) = lista.sortedWith(compareBy<OrientacaoResposta> { it.arquivadaEm != null }.thenByDescending { it.criadoEm })
