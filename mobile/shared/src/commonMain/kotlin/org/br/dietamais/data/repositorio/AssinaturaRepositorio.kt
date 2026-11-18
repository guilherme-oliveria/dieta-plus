package org.br.dietamais.data.repositorio

import kotlinx.serialization.json.encodeToJsonElement
import org.br.dietamais.data.remoto.*
import org.br.dietamais.data.remoto.dto.*

interface PortaAssinatura {
    suspend fun obter(): AssinaturaResposta
    /** Subir ou assinar devolve a URL do checkout; descer com a assinatura ativa só agenda a troca (RN-ASS-10). */
    suspend fun pedirCheckout(plano: String, periodicidade: Periodicidade = Periodicidade.MENSAL): CheckoutResposta
}

class AssinaturaRepositorio(private val api: ApiDieta) : PortaAssinatura {
    override suspend fun obter(): AssinaturaResposta {
        val contexto = contexto()
        return api.obter<AssinaturaResposta>("assinatura").also { conferir(contexto) }
    }
    override suspend fun pedirCheckout(plano: String, periodicidade: Periodicidade): CheckoutResposta {
        val contexto = contexto()
        return api.enviar<CheckoutResposta>("assinatura/checkout", JsonContrato.encodeToJsonElement(CheckoutRequisicao(plano, periodicidade))).also { conferir(contexto) }
    }
    private fun contexto() = api.operacaoSessao.value?.takeIf { it.contexto.perfil == Perfil.NUTRICIONISTA } ?: throw SessaoAlterada()
    private fun conferir(esperado: OperacaoSessao) { if (api.operacaoSessao.value != esperado) throw SessaoAlterada() }
}
