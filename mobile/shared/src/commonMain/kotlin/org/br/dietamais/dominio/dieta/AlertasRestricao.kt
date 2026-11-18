package org.br.dietamais.dominio.dieta

import org.br.dietamais.data.remoto.dto.GravidadeRestricao
import org.br.dietamais.data.remoto.dto.RestricaoSaudeDto
import org.br.dietamais.data.remoto.dto.TipoRestricaoSaude
import kotlin.uuid.Uuid

/**
 * Alerta de restrição num item da dieta (RN-DIE-14). Alergia exige a confirmação da nutri para publicar;
 * intolerância só avisa. O marcador do alimento é sugestão: a conduta é da nutri.
 */
data class AlertaItem(val restricaoId: Uuid, val tipo: TipoRestricaoSaude, val nome: String, val marcador: String,
    val gravidade: GravidadeRestricao? = null) {
    val exigeConfirmacao get() = tipo == TipoRestricaoSaude.ALERGIA
    override fun toString() = "AlertaItem(tipo=$tipo)"
}

/** Mesma regra do backend (vetores `contrato/calculos/alertas-restricao.json`), para o montador avisar também sem rede. */
object AlertasRestricao {
    /** Um alerta por restrição `ALERGIA` ou `INTOLERANCIA` e marcador em comum com o item, na ordem da ficha e dos marcadores da restrição. */
    fun alertasItem(restricoes: List<RestricaoSaudeDto>, marcadoresItem: Collection<String>): List<AlertaItem> {
        if (marcadoresItem.isEmpty()) return emptyList()
        val doItem = marcadoresItem.toSet()
        return restricoes.filter { it.tipo == TipoRestricaoSaude.ALERGIA || it.tipo == TipoRestricaoSaude.INTOLERANCIA }
            .flatMap { r -> r.marcadores.distinct().filter { it in doItem }.map { AlertaItem(r.id, r.tipo, r.nome, it, r.gravidade) } }
    }

    /** Restrições que ligam marcadores (as que podem gerar alerta), para o resumo no topo do montador. */
    fun relevantes(restricoes: List<RestricaoSaudeDto>) =
        restricoes.filter { (it.tipo == TipoRestricaoSaude.ALERGIA || it.tipo == TipoRestricaoSaude.INTOLERANCIA) && it.marcadores.isNotEmpty() }
}
