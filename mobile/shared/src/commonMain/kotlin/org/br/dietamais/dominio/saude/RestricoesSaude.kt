package org.br.dietamais.dominio.saude

import org.br.dietamais.data.remoto.dto.GravidadeRestricao
import org.br.dietamais.data.remoto.dto.RestricaoSaudeDto
import org.br.dietamais.data.remoto.dto.TipoRestricaoSaude
import kotlin.uuid.Uuid

/** Selo do cabeçalho da ficha (DES-16, `A-Saude-Ficha`): a alergia ou intolerância mais séria e quantas outras há. */
data class SeloSaude(val principal: RestricaoSaudeDto, val outras: Int)

enum class ErroItemSaude { NOME_OBRIGATORIO, NOME_LONGO, OBSERVACAO_LONGA, LIMITE_ITENS }

/** Formulário da folha "Novo item de saúde" (`A-Saude-Item`). `id` nulo = item novo. */
data class FormularioItemSaude(val id: Uuid? = null, val tipo: TipoRestricaoSaude = TipoRestricaoSaude.ALERGIA, val nome: String = "",
    val gravidade: GravidadeRestricao? = null, val observacao: String = "", val marcadores: Set<String> = emptySet(),
    /** Depois que a nutri mexe nos marcadores, o nome para de sugerir. */ val marcadoresEscolhidos: Boolean = false) {
    override fun toString() = "FormularioItemSaude[conteúdo protegido]"
}

/**
 * Regras de cliente da seção "Saúde e restrições" (MOB-82, RN-SAU-01 e 02). O backend decide; aqui é só feedback e montagem da lista.
 * A lista inteira vai junto com a ficha e substitui a anterior.
 */
object RestricoesSaude {
    const val LIMITE_NOME = 120
    const val LIMITE_OBSERVACAO = 500
    const val LIMITE_ITENS = 50

    /** Marcadores de alimento que a nutri pode ligar, na ordem da folha (BE-69). Condição e medicamento não têm marcador (RN-SAU-02). */
    val MARCADORES = listOf("AMENDOIM", "CONTEM_LACTOSE", "CONTEM_GLUTEN", "OVO", "FRUTOS_DO_MAR", "SOJA")

    fun ligaMarcadores(tipo: TipoRestricaoSaude) = tipo == TipoRestricaoSaude.ALERGIA || tipo == TipoRestricaoSaude.INTOLERANCIA

    private val SUGESTOES = listOf(
        "AMENDOIM" to listOf("amendoim"),
        "CONTEM_LACTOSE" to listOf("lactose", "leite", "laticin"),
        "CONTEM_GLUTEN" to listOf("gluten", "trigo", "celiac"),
        "OVO" to listOf("ovo"),
        "FRUTOS_DO_MAR" to listOf("frutos do mar", "camarao", "marisco", "crustace", "lagosta", "lula", "polvo", "ostra"),
        "SOJA" to listOf("soja"),
    )

    /** Sugere marcadores pelo nome ("Sugerido pelo nome"), sem acento e sem diferença de maiúsculas. */
    fun sugerirMarcadores(nome: String): Set<String> {
        val n = normalizar(nome)
        if (n.isBlank()) return emptySet()
        return SUGESTOES.filter { (_, termos) -> termos.any { t -> Regex("(^|[^a-z])" + Regex.escape(t)).containsMatchIn(n) } }.map { it.first }.toSet()
    }

    fun validar(f: FormularioItemSaude, quantidadeAtual: Int): ErroItemSaude? = when {
        f.nome.isBlank() -> ErroItemSaude.NOME_OBRIGATORIO
        f.nome.trim().length > LIMITE_NOME -> ErroItemSaude.NOME_LONGO
        f.observacao.trim().length > LIMITE_OBSERVACAO -> ErroItemSaude.OBSERVACAO_LONGA
        f.id == null && quantidadeAtual >= LIMITE_ITENS -> ErroItemSaude.LIMITE_ITENS
        else -> null
    }

    /** Item pronto para a lista: marcadores só em alergia e intolerância, na ordem da folha; textos sem espaço nas pontas. */
    fun item(f: FormularioItemSaude, novoId: () -> Uuid): RestricaoSaudeDto = RestricaoSaudeDto(
        id = f.id ?: novoId(), tipo = f.tipo, nome = f.nome.trim(), gravidade = f.gravidade.takeIf { it != GravidadeRestricao.OUTRO },
        observacao = f.observacao.trim().ifBlank { null },
        marcadores = if (ligaMarcadores(f.tipo)) MARCADORES.filter { it in f.marcadores } + f.marcadores.filter { it !in MARCADORES }.sorted() else emptyList())

    /** Substitui o item de mesmo id ou acrescenta no fim. */
    fun salvarNaLista(lista: List<RestricaoSaudeDto>, item: RestricaoSaudeDto): List<RestricaoSaudeDto> =
        if (lista.any { it.id == item.id }) lista.map { if (it.id == item.id) item else it } else lista + item

    fun remover(lista: List<RestricaoSaudeDto>, id: Uuid) = lista.filterNot { it.id == id }

    fun formularioDe(r: RestricaoSaudeDto) = FormularioItemSaude(r.id, r.tipo.takeIf { it != TipoRestricaoSaude.OUTRO } ?: TipoRestricaoSaude.CONDICAO, r.nome,
        r.gravidade.takeIf { it != GravidadeRestricao.OUTRO }, r.observacao.orEmpty(), r.marcadores.toSet(), marcadoresEscolhidos = true)

    /** Ordem da seção: alergias, intolerâncias, condições, medicamentos; dentro do tipo, a ordem em que foram cadastrados. */
    fun ordenar(lista: List<RestricaoSaudeDto>) = lista.withIndex().sortedWith(compareBy({ ordemTipo(it.value.tipo) }, { it.index })).map { it.value }

    fun selo(lista: List<RestricaoSaudeDto>): SeloSaude? {
        val relevantes = lista.filter { ligaMarcadores(it.tipo) }
        val principal = relevantes.withIndex().minWithOrNull(compareBy({ ordemTipo(it.value.tipo) }, { ordemGravidade(it.value.gravidade) }, { it.index }))?.value ?: return null
        return SeloSaude(principal, relevantes.size - 1)
    }

    private fun ordemTipo(t: TipoRestricaoSaude) = when (t) {
        TipoRestricaoSaude.ALERGIA -> 0; TipoRestricaoSaude.INTOLERANCIA -> 1; TipoRestricaoSaude.CONDICAO -> 2; TipoRestricaoSaude.MEDICAMENTO -> 3; TipoRestricaoSaude.OUTRO -> 4
    }
    private fun ordemGravidade(g: GravidadeRestricao?) = when (g) { GravidadeRestricao.GRAVE -> 0; GravidadeRestricao.MODERADA -> 1; GravidadeRestricao.LEVE -> 2; else -> 3 }

    private fun normalizar(s: String): String {
        val mapa = mapOf('á' to 'a', 'à' to 'a', 'â' to 'a', 'ã' to 'a', 'ä' to 'a', 'é' to 'e', 'ê' to 'e', 'è' to 'e', 'í' to 'i', 'ì' to 'i', 'î' to 'i',
            'ó' to 'o', 'ô' to 'o', 'õ' to 'o', 'ò' to 'o', 'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u', 'ç' to 'c')
        return s.lowercase().map { mapa[it] ?: it }.joinToString("")
    }
}
