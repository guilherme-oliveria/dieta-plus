package com.br.dietamais.assinaturas.api;

import com.br.dietamais.assinaturas.aplicacao.ServicoAssinatura;
import com.br.dietamais.comum.api.ProblemaResposta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Planos à venda, sem login (RN-ASS-09, BE-80): a página de preços do site e o app leem daqui. */
@RestController
@RequestMapping("/api/v1/planos")
@Tag(name="assinaturas")
public class PlanoPublicoController {
	private final ServicoAssinatura servico;
	public PlanoPublicoController(ServicoAssinatura servico){this.servico=servico;}

	@GetMapping
	@Operation(operationId="listarPlanosPublicos",summary="Planos à venda (público)",description="Só planos ativos e à venda, do mais barato ao mais caro; `limitePacientes` nulo = sem limite; `precoAnual` nulo = sem venda anual. Sem login, com limite de requisições por IP (`TAXA_EXCEDIDA`). RN-ASS-09. BE-80.")
	@ApiResponse(responseCode="200",description="Planos",content=@Content(mediaType="application/json",schema=@Schema(implementation=PlanosPublicosResposta.class)))
	@ApiResponse(responseCode="429",description="TAXA_EXCEDIDA",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class)))
	public ResponseEntity<PlanosPublicosResposta> listar(){
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(60,TimeUnit.SECONDS).cachePublic()).body(new PlanosPublicosResposta(servico.planosPagos()));
	}
}
