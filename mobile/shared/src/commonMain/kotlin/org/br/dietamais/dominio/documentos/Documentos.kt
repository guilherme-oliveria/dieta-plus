package org.br.dietamais.dominio.documentos

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.br.dietamais.data.remoto.dto.*
import org.br.dietamais.dominio.agenda.formatarData
import org.br.dietamais.dominio.agenda.formatarHora
import org.br.dietamais.dominio.agenda.lerData
import org.br.dietamais.dominio.agenda.lerHora
import kotlin.uuid.Uuid

const val LIMITE_NOME_MODELO_DOC = 80
const val LIMITE_TEXTO_DOC = 5000
const val LIMITE_MOTIVO_ANULACAO = 300

/** Campos de preenchimento que o backend troca na emissão (BE-78). */
val CAMPOS_DOCUMENTO = listOf("paciente", "data", "horario", "nutri", "crn")

/** Valores da prévia: o servidor preenche de verdade; a prévia só mostra à nutri o que vai sair (RN-DOC-02). */
data class CamposPrevia(val paciente: String, val data: String, val horario: String, val nutri: String, val crn: String)

/** Trecho da prévia: [campo] marca o que veio da ficha ou da consulta (destacado em verde no artboard `A-Documento-Emitir`). */
data class TrechoPrevia(val texto: String, val campo: Boolean = false)

/** Troca `{{campo}}` pelos valores; campo vazio vira "___" para a nutri ver que falta; campo desconhecido fica como está. */
fun previaDocumento(texto: String, valores: CamposPrevia): List<TrechoPrevia> {
    val mapa = mapOf("paciente" to valores.paciente, "data" to valores.data, "horario" to valores.horario, "nutri" to valores.nutri, "crn" to valores.crn)
    val saida = mutableListOf<TrechoPrevia>()
    var resto = texto
    val padrao = Regex("\\{\\{\\s*([a-z]+)\\s*\\}\\}")
    while (true) {
        val m = padrao.find(resto) ?: break
        if (m.range.first > 0) saida += TrechoPrevia(resto.substring(0, m.range.first))
        val valor = mapa[m.groupValues[1]]
        saida += if (valor == null) TrechoPrevia(m.value) else TrechoPrevia(valor.ifBlank { "___" }, campo = true)
        resto = resto.substring(m.range.last + 1)
    }
    if (resto.isNotEmpty()) saida += TrechoPrevia(resto)
    return saida
}

/** Campos que o texto do modelo usa, na ordem de [CAMPOS_DOCUMENTO]. */
fun camposDoTexto(texto: String): List<String> = CAMPOS_DOCUMENTO.filter { Regex("\\{\\{\\s*$it\\s*\\}\\}").containsMatchIn(texto) }

/** Os do sistema primeiro (declaração e atestado), depois os da nutri por nome. */
fun modelosOrdenados(modelos: List<ModeloDocumentoResposta>): List<ModeloDocumentoResposta> =
    modelos.sortedWith(compareByDescending<ModeloDocumentoResposta> { it.doSistema }.thenBy { it.tipo.ordinal }.thenBy { it.nome.lowercase() })

/** Consultas realizadas deste paciente, da mais recente: só elas preenchem a declaração de comparecimento (BE-78). */
fun consultasParaDeclaracao(consultas: List<ConsultaResposta>, pacienteId: Uuid): List<ConsultaResposta> =
    consultas.filter { it.pacienteId == pacienteId && it.status == StatusConsulta.REALIZADA }.sortedByDescending { it.inicio }

enum class ErroEmissao { MODELO, CONSULTA, DATA, HORARIO }

/** Formulário de emissão. Declaração: escolhe uma consulta realizada; atestado: data (padrão hoje) e horário opcional. */
data class FormularioEmissao(val modeloId: Uuid? = null, val tipo: TipoDocumento? = null, val consultaId: Uuid? = null,
    val data: String = "", val horario: String = "")

fun validarEmissao(f: FormularioEmissao): Set<ErroEmissao> {
    val erros = mutableSetOf<ErroEmissao>()
    if (f.modeloId == null) erros += ErroEmissao.MODELO
    if (f.tipo == TipoDocumento.DECLARACAO_COMPARECIMENTO) {
        if (f.consultaId == null) erros += ErroEmissao.CONSULTA
    } else {
        if (f.data.isNotBlank() && lerData(f.data) == null) erros += ErroEmissao.DATA
        if (f.horario.isNotBlank() && lerHora(f.horario) == null) erros += ErroEmissao.HORARIO
    }
    return erros
}

/** Pedido de emissão: com consulta o backend tira data e horário dela; sem, manda o que a nutri digitou (vazio = hoje no servidor). */
fun requisicaoEmissao(f: FormularioEmissao, id: Uuid): EmitirDocumentoRequisicao {
    val modelo = requireNotNull(f.modeloId)
    if (f.tipo == TipoDocumento.DECLARACAO_COMPARECIMENTO) return EmitirDocumentoRequisicao(id, modelo, consultaId = f.consultaId)
    val hora = lerHora(f.horario)?.let { (h, m) -> "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}" }
    return EmitirDocumentoRequisicao(id, modelo, data = lerData(f.data), horario = hora)
}

/** Valores da prévia a partir do formulário; com consulta, data e horário dela ("15:00 às 15:50"). */
fun camposPrevia(f: FormularioEmissao, paciente: String, nutri: String, crn: String, consulta: ConsultaResposta?, hoje: LocalDate, fuso: TimeZone): CamposPrevia =
    if (consulta != null) CamposPrevia(paciente, formatarData(org.br.dietamais.dominio.agenda.diaLocal(consulta.inicio, fuso)),
        "${formatarHora(consulta.inicio, fuso)} às ${formatarHora(consulta.fim, fuso)}", nutri, crn)
    else CamposPrevia(paciente, lerData(f.data)?.let(::formatarData) ?: if (f.data.isBlank()) formatarData(hoje) else "",
        lerHora(f.horario)?.let { (h, m) -> "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}" } ?: "", nutri, crn)

enum class ErroModeloDoc { NOME, TEXTO }

data class FormularioModeloDoc(val tipo: TipoDocumento = TipoDocumento.DECLARACAO_COMPARECIMENTO, val nome: String = "", val texto: String = "",
    val modeloId: Uuid? = null, val versao: Long? = null) {
    val edicao get() = modeloId != null
}

/** "Copiar para editar" um modelo do sistema: mesmo tipo e texto, nome sugerido; editar um dela mantém id e versão. */
fun formularioDeModelo(m: ModeloDocumentoResposta, sufixoCopia: String): FormularioModeloDoc =
    if (m.doSistema) FormularioModeloDoc(m.tipo, "${m.nome} $sufixoCopia".take(LIMITE_NOME_MODELO_DOC), m.texto)
    else FormularioModeloDoc(m.tipo, m.nome, m.texto, m.id, m.versao)

fun validarModeloDoc(f: FormularioModeloDoc): Set<ErroModeloDoc> {
    val erros = mutableSetOf<ErroModeloDoc>()
    if (f.nome.isBlank() || f.nome.trim().length > LIMITE_NOME_MODELO_DOC) erros += ErroModeloDoc.NOME
    if (f.texto.isBlank() || f.texto.trim().length > LIMITE_TEXTO_DOC) erros += ErroModeloDoc.TEXTO
    return erros
}

fun requisicaoModeloDoc(f: FormularioModeloDoc) = ModeloDocumentoRequisicao(f.tipo, f.nome.trim(), f.texto.trim())

/** Motivo da anulação: obrigatório, até 300 caracteres (RN-DOC-02). */
fun motivoValido(motivo: String) = motivo.isNotBlank() && motivo.trim().length <= LIMITE_MOTIVO_ANULACAO

/** Mais novos primeiro (o backend já manda assim; a ordem não depende disso). */
fun documentosOrdenados(lista: List<DocumentoResposta>) = lista.sortedWith(compareByDescending<DocumentoResposta> { it.emitidoEm }.thenBy { it.id.toString() })
