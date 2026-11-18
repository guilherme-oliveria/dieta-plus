package com.br.dietamais.documentos.api;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.documentos.aplicacao.ConferirDocumento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Rota pública (sem login), com limite por IP no `FiltroTaxaLogin`. */
@RestController
@RequestMapping("/api/v1/documentos/conferir")
@Tag(name="documentos")
public class ConferenciaDocumentoController {
	private final ConferirDocumento conferir;
	public ConferenciaDocumentoController(ConferirDocumento conferir){this.conferir=conferir;}

	@GetMapping("/{codigo}")
	@Operation(operationId="conferirDocumento",summary="Conferir documento pelo código",description="RN-DOC-03; pública. Aceita o código com ou sem hífen, em minúsculas. Mostra tipo, emissor, CRN, data e `VALIDO` ou `ANULADO`; "
		+"nenhum dado do paciente. Código inexistente ou fora do formato: `404 RECURSO_NAO_ENCONTRADO`. Limite por IP: `429 TAXA_EXCEDIDA` com `Retry-After`. Toda tentativa fica registrada; BE-79.")
	@ApiResponse(responseCode="200",description="Documento encontrado",content=@Content(mediaType="application/json",schema=@Schema(implementation=ConferenciaDocumentoResposta.class)))
	@ApiResponse(responseCode="404",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class)))
	@ApiResponse(responseCode="429",description="ProblemDetail com código estável (`TAXA_EXCEDIDA`)",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class)))
	public ResponseEntity<ConferenciaDocumentoResposta> conferir(@PathVariable String codigo,HttpServletRequest requisicao){
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(conferir.conferir(codigo,requisicao.getRemoteAddr()));
	}
}
