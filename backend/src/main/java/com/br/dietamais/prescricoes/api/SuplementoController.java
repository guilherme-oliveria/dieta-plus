package com.br.dietamais.prescricoes.api;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.prescricoes.aplicacao.GerenciarSuplementos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
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
@RequestMapping("/api/v1")
@Tag(name="prescricoes")
@SecurityRequirement(name="bearerAuth")
@RotaNutri
@ApiResponses({
	@ApiResponse(responseCode="400",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="401",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="403",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="404",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="409",description="Conflito de versão ou idempotência; atual só se autorizado",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="422",description="ProblemDetail com código estável (`ITEM_INVALIDO`)",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class)))
})
public class SuplementoController {
	private final GerenciarSuplementos suplementos;
	public SuplementoController(GerenciarSuplementos suplementos){this.suplementos=suplementos;}

	@GetMapping("/pacientes/{id}/suplementos")
	@Operation(operationId="listarSuplementos",summary="Suplementos prescritos ao paciente",description="RN-SUP-01; inclui os encerrados; BE-75.")
	@ApiResponse(responseCode="200",description="Suplementos",content=@Content(mediaType="application/json",schema=@Schema(implementation=SuplementosResposta.class)))
	public ResponseEntity<SuplementosResposta> listar(@PathVariable UUID id){
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(suplementos.listar(id));
	}

	@PostMapping("/pacientes/{id}/suplementos")
	@Idempotente
	@Operation(operationId="prescreverSuplemento",summary="Prescrever suplemento",description="RN-SUP-01; `produtoId` ou `nome`, nunca os dois nem nenhum (`ITEM_INVALIDO`); nome e dose ficam congelados; vínculo encerrado só lê; escrita online; BE-75.")
	@ApiResponse(responseCode="201",description="Prescrição criada",content=@Content(mediaType="application/json",schema=@Schema(implementation=SuplementoResposta.class)),headers=@Header(name="ETag",description="Versão forte da entidade para If-Match",schema=@Schema(type="string")))
	public ResponseEntity<SuplementoResposta> criar(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) UUID chave,@Valid @RequestBody SuplementoRequisicao requisicao){
		return entidade(suplementos.criar(id,requisicao),201);
	}

	@PatchMapping("/suplementos/{id}")
	@Operation(operationId="atualizarSuplemento",summary="Ajustar ou encerrar suplemento",description="RN-SUP-02, RN-SYN-02; ou ajusta dose, forma, frequência, horários e fim, ou `encerrar=true` (fecha hoje, sozinho); nome e produto não mudam; BE-75.")
	@ApiResponse(responseCode="200",description="Prescrição atualizada",content=@Content(mediaType="application/json",schema=@Schema(implementation=SuplementoResposta.class)),headers=@Header(name="ETag",description="Versão forte da entidade para If-Match",schema=@Schema(type="string")))
	public ResponseEntity<SuplementoResposta> atualizar(@PathVariable UUID id,@Parameter(description="Versão entre aspas, por exemplo \"1\"") @RequestHeader("If-Match") String ifMatch,@Valid @RequestBody AtualizarSuplementoRequisicao requisicao){
		return entidade(suplementos.atualizar(id,ifMatch,requisicao),200);
	}

	private ResponseEntity<SuplementoResposta> entidade(SuplementoResposta r,int status){return ResponseEntity.status(status).eTag(Long.toString(r.versao())).cacheControl(CacheControl.noStore()).body(r);}
}
