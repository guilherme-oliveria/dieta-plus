package com.br.dietamais.metas.api;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.metas.aplicacao.GerenciarModelosMeta;
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
@Tag(name="metas")
@SecurityRequirement(name="bearerAuth")
@RotaNutri
@ApiResponses({
	@ApiResponse(responseCode="400",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="401",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="403",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="404",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="409",description="Conflito de versão ou idempotência",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="422",description="ProblemDetail com código estável (`META_INVALIDA`)",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class)))
})
public class ModeloMetaController {
	private final GerenciarModelosMeta modelos;
	public ModeloMetaController(GerenciarModelosMeta modelos){this.modelos=modelos;}

	@GetMapping("/eu/nutri/modelos-meta")
	@Operation(operationId="listarModelosMeta",summary="Modelos de metas da nutri",description="RN-MET-04; BE-74b.")
	@ApiResponse(responseCode="200",description="Modelos",content=@Content(mediaType="application/json",schema=@Schema(implementation=ModelosMetaResposta.class)))
	public ResponseEntity<ModelosMetaResposta> listar(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(modelos.listar());}

	@PostMapping("/eu/nutri/modelos-meta")
	@Idempotente
	@Operation(operationId="criarModeloMeta",summary="Criar modelo de metas",description="RN-MET-04; a água não entra em modelo (`META_INVALIDA`); BE-74b.")
	@ApiResponse(responseCode="201",description="Modelo criado",content=@Content(mediaType="application/json",schema=@Schema(implementation=ModeloMetaResposta.class)),headers=@Header(name="ETag",description="Versão forte da entidade",schema=@Schema(type="string")))
	public ResponseEntity<ModeloMetaResposta> criar(@RequestHeader(value="Idempotency-Key",required=false) UUID chave,@Valid @RequestBody ModeloMetaRequisicao requisicao){
		return entidade(modelos.criar(requisicao),201);
	}

	@PutMapping("/eu/nutri/modelos-meta/{id}")
	@Operation(operationId="atualizarModeloMeta",summary="Substituir modelo de metas",description="RN-MET-04, RN-SYN-02; metas já criadas a partir dele não mudam; BE-74b.")
	@ApiResponse(responseCode="200",description="Modelo atualizado",content=@Content(mediaType="application/json",schema=@Schema(implementation=ModeloMetaResposta.class)),headers=@Header(name="ETag",description="Nova versão",schema=@Schema(type="string")))
	public ResponseEntity<ModeloMetaResposta> atualizar(@PathVariable UUID id,@Parameter(description="Versão entre aspas, por exemplo \"1\"") @RequestHeader("If-Match") String ifMatch,@Valid @RequestBody ModeloMetaRequisicao requisicao){
		return entidade(modelos.atualizar(id,ifMatch,requisicao),200);
	}

	@DeleteMapping("/eu/nutri/modelos-meta/{id}")
	@Operation(operationId="excluirModeloMeta",summary="Apagar modelo de metas",description="RN-MET-04; metas já criadas não mudam; BE-74b.")
	@ApiResponse(responseCode="204",description="Apagado")
	public ResponseEntity<Void> excluir(@PathVariable UUID id,@RequestHeader("If-Match") String ifMatch){
		modelos.excluir(id,ifMatch);return ResponseEntity.noContent().build();
	}

	@PostMapping("/pacientes/{id}/metas/de-modelo/{modeloId}")
	@Idempotente
	@Operation(operationId="criarMetasDeModelo",summary="Criar as metas de um modelo para o paciente",description="RN-MET-04; vínculo encerrado só lê; online; BE-74b.")
	@ApiResponse(responseCode="201",description="Metas criadas",content=@Content(mediaType="application/json",schema=@Schema(implementation=MetasResposta.class)))
	public ResponseEntity<MetasResposta> criarDeModelo(@PathVariable UUID id,@PathVariable UUID modeloId,@RequestHeader(value="Idempotency-Key",required=false) UUID chave){
		return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(modelos.aplicar(id,modeloId));
	}

	private ResponseEntity<ModeloMetaResposta> entidade(ModeloMetaResposta r,int status){return ResponseEntity.status(status).eTag(Long.toString(r.versao())).cacheControl(CacheControl.noStore()).body(r);}
}
