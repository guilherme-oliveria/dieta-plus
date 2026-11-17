package com.br.dietamais.prescricoes.api;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.prescricoes.aplicacao.*;
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
	@ApiResponse(responseCode="422",description="ProblemDetail com código estável (`FORMATO_INVALIDO`)",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class)))
})
public class OrientacaoController {
	private final GerenciarOrientacoes orientacoes;private final GerenciarModelosOrientacao modelos;
	public OrientacaoController(GerenciarOrientacoes orientacoes,GerenciarModelosOrientacao modelos){this.orientacoes=orientacoes;this.modelos=modelos;}

	@GetMapping("/pacientes/{id}/orientacoes")
	@Operation(operationId="listarOrientacoes",summary="Orientações enviadas ao paciente",description="RN-ORI-01; inclui as arquivadas; BE-76.")
	@ApiResponse(responseCode="200",description="Orientações",content=@Content(mediaType="application/json",schema=@Schema(implementation=OrientacoesResposta.class)))
	public ResponseEntity<OrientacoesResposta> listar(@PathVariable UUID id){
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orientacoes.listar(id));
	}

	@PostMapping("/pacientes/{id}/orientacoes")
	@Idempotente
	@Operation(operationId="criarOrientacao",summary="Enviar orientação",description="RN-ORI-01; Markdown restrito, HTML é descartado; dispara push genérico; vínculo encerrado só lê; escrita online; BE-76.")
	@ApiResponse(responseCode="201",description="Orientação criada",content=@Content(mediaType="application/json",schema=@Schema(implementation=OrientacaoResposta.class)),headers=@Header(name="ETag",description="Versão forte da entidade para If-Match",schema=@Schema(type="string")))
	public ResponseEntity<OrientacaoResposta> criar(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) UUID chave,@Valid @RequestBody OrientacaoRequisicao requisicao){
		return entidade(orientacoes.criar(id,requisicao),201);
	}

	@PatchMapping("/orientacoes/{id}")
	@Operation(operationId="atualizarOrientacao",summary="Editar ou arquivar orientação",description="RN-ORI-01, RN-SYN-02; ou edita título e texto, ou `arquivar=true` (sozinho); nada é apagado; BE-76.")
	@ApiResponse(responseCode="200",description="Orientação atualizada",content=@Content(mediaType="application/json",schema=@Schema(implementation=OrientacaoResposta.class)),headers=@Header(name="ETag",description="Versão forte da entidade para If-Match",schema=@Schema(type="string")))
	public ResponseEntity<OrientacaoResposta> atualizar(@PathVariable UUID id,@Parameter(description="Versão entre aspas, por exemplo \"1\"") @RequestHeader("If-Match") String ifMatch,@Valid @RequestBody AtualizarOrientacaoRequisicao requisicao){
		return entidade(orientacoes.atualizar(id,ifMatch,requisicao),200);
	}

	@GetMapping("/eu/nutri/modelos-orientacao")
	@Operation(operationId="listarModelosOrientacao",summary="Modelos de orientação da nutri",description="RN-ORI-01; BE-76.")
	@ApiResponse(responseCode="200",description="Modelos",content=@Content(mediaType="application/json",schema=@Schema(implementation=ModelosOrientacaoResposta.class)))
	public ResponseEntity<ModelosOrientacaoResposta> listarModelos(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(modelos.listar());}

	@PostMapping("/eu/nutri/modelos-orientacao")
	@Idempotente
	@Operation(operationId="criarModeloOrientacao",summary="Criar modelo de orientação",description="RN-ORI-01; BE-76.")
	@ApiResponse(responseCode="201",description="Modelo criado",content=@Content(mediaType="application/json",schema=@Schema(implementation=ModeloOrientacaoResposta.class)),headers=@Header(name="ETag",description="Versão forte da entidade",schema=@Schema(type="string")))
	public ResponseEntity<ModeloOrientacaoResposta> criarModelo(@RequestHeader(value="Idempotency-Key",required=false) UUID chave,@Valid @RequestBody ModeloOrientacaoRequisicao requisicao){
		var m=modelos.criar(requisicao);return ResponseEntity.status(201).eTag(Long.toString(m.versao())).cacheControl(CacheControl.noStore()).body(m);
	}

	@PutMapping("/eu/nutri/modelos-orientacao/{id}")
	@Operation(operationId="atualizarModeloOrientacao",summary="Substituir modelo de orientação",description="RN-ORI-01, RN-SYN-02; orientações já enviadas não mudam; BE-76.")
	@ApiResponse(responseCode="200",description="Modelo atualizado",content=@Content(mediaType="application/json",schema=@Schema(implementation=ModeloOrientacaoResposta.class)),headers=@Header(name="ETag",description="Nova versão",schema=@Schema(type="string")))
	public ResponseEntity<ModeloOrientacaoResposta> atualizarModelo(@PathVariable UUID id,@Parameter(description="Versão entre aspas, por exemplo \"1\"") @RequestHeader("If-Match") String ifMatch,@Valid @RequestBody ModeloOrientacaoRequisicao requisicao){
		var m=modelos.atualizar(id,ifMatch,requisicao);return ResponseEntity.ok().eTag(Long.toString(m.versao())).cacheControl(CacheControl.noStore()).body(m);
	}

	@DeleteMapping("/eu/nutri/modelos-orientacao/{id}")
	@Operation(operationId="excluirModeloOrientacao",summary="Apagar modelo de orientação",description="RN-ORI-01; orientações já enviadas não mudam; BE-76.")
	@ApiResponse(responseCode="204",description="Apagado")
	public ResponseEntity<Void> excluirModelo(@PathVariable UUID id,@RequestHeader("If-Match") String ifMatch){
		modelos.excluir(id,ifMatch);return ResponseEntity.noContent().build();
	}

	private ResponseEntity<OrientacaoResposta> entidade(OrientacaoResposta r,int status){return ResponseEntity.status(status).eTag(Long.toString(r.versao())).cacheControl(CacheControl.noStore()).body(r);}
}
