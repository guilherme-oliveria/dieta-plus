package com.br.dietamais.metas.api;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.metas.aplicacao.RegistrarMetas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/eu")
@Tag(name="metas")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({
	@ApiResponse(responseCode="400",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="401",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="403",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="404",description="Meta que não é do paciente ou de vínculo encerrado",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="409",description="Id já usado por outro paciente",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class)))
})
public class RegistroMetaController {
	private final RegistrarMetas registros;
	public RegistroMetaController(RegistrarMetas registros){this.registros=registros;}

	@PostMapping("/registros-meta")
	@Idempotente
	@Operation(operationId="registrarMeta",summary="Registrar progresso de uma meta",description="RN-MET-02; só inclusão, repetir o id devolve o mesmo registro; mesmas janelas de data do check-in; SIM_NAO usa 1 ou 0; BE-74.")
	@ApiResponse(responseCode="201",description="Registro gravado",content=@Content(mediaType="application/json",schema=@Schema(implementation=RegistroMetaResposta.class)))
	public ResponseEntity<RegistroMetaResposta> registrarMeta(@RequestHeader(value="Idempotency-Key",required=false) UUID chave,@Valid @RequestBody RegistroMetaRequisicao requisicao){
		return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(registros.registrar(requisicao));
	}
}
