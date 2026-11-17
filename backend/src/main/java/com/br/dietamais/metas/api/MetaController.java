package com.br.dietamais.metas.api;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.metas.aplicacao.GerenciarMetas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name="metas")
@SecurityRequirement(name="bearerAuth")
@RotaNutri
@ApiResponses({
	@ApiResponse(responseCode="400",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="401",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="403",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="404",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="409",description="Conflito de versão ou idempotência; atual só se autorizado",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="422",description="ProblemDetail com código estável (`META_INVALIDA`)",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class)))
})
public class MetaController {
	private final GerenciarMetas metas;
	public MetaController(GerenciarMetas metas){this.metas=metas;}

	@GetMapping("/pacientes/{id}/metas")
	@Operation(operationId="listarMetas",summary="Metas do paciente",description="RN-MET-01; metas da nutri para o paciente (inclui as encerradas); BE-74.")
	@ApiResponse(responseCode="200",description="Metas",content=@Content(mediaType="application/json",schema=@Schema(implementation=MetasResposta.class)))
	public ResponseEntity<MetasResposta> listarMetas(@PathVariable UUID id){
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(metas.listar(id));
	}

	@PostMapping("/pacientes/{id}/metas")
	@Idempotente
	@Operation(operationId="criarMeta",summary="Criar meta de hábito",description="RN-MET-01; vínculo encerrado só lê; meta inválida: 422 `META_INVALIDA`; escrita online; BE-74.")
	@ApiResponse(responseCode="201",description="Meta criada",content=@Content(mediaType="application/json",schema=@Schema(implementation=MetaResposta.class)),headers=@Header(name="ETag",description="Versão forte da entidade para If-Match",schema=@Schema(type="string")))
	public ResponseEntity<MetaResposta> criarMeta(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) UUID chave,@Valid @RequestBody MetaRequisicao requisicao){
		return entidade(metas.criar(id,requisicao),201);
	}

	@PatchMapping("/metas/{id}")
	@Operation(operationId="atualizarMeta",summary="Ajustar ou encerrar meta",description="RN-MET-01, RN-SYN-02; campos ausentes não mudam; `encerrar=true` fecha a meta hoje (sozinho, sem outros campos); BE-74.")
	@ApiResponse(responseCode="200",description="Meta atualizada",content=@Content(mediaType="application/json",schema=@Schema(implementation=MetaResposta.class)),headers=@Header(name="ETag",description="Versão forte da entidade para If-Match",schema=@Schema(type="string")))
	public ResponseEntity<MetaResposta> atualizarMeta(@PathVariable UUID id,@Parameter(description="Versão entre aspas, por exemplo \"1\"") @RequestHeader("If-Match") String ifMatch,@Valid @RequestBody AtualizarMetaRequisicao requisicao){
		return entidade(metas.atualizar(id,ifMatch,requisicao),200);
	}

	@GetMapping("/pacientes/{id}/metas/cumprimento")
	@Operation(operationId="consultarCumprimentoMetas",summary="Cumprimento das metas",description="RN-MET-03; padrão: últimos 7 dias até hoje; no máximo 366 dias; BE-74.")
	@ApiResponse(responseCode="200",description="Cumprimento por meta",content=@Content(mediaType="application/json",schema=@Schema(implementation=CumprimentoMetasResposta.class)))
	public ResponseEntity<CumprimentoMetasResposta> consultarCumprimento(@PathVariable UUID id,
			@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate de,
			@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate ate){
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(metas.cumprimento(id,de,ate));
	}

	private ResponseEntity<MetaResposta> entidade(MetaResposta r,int status){return ResponseEntity.status(status).eTag(Long.toString(r.versao())).cacheControl(CacheControl.noStore()).body(r);}
}
