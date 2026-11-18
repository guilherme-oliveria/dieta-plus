package com.br.dietamais.documentos.api;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.documentos.aplicacao.*;
import com.br.dietamais.relatorios.api.PdfResposta;
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
@Tag(name="documentos")
@SecurityRequirement(name="bearerAuth")
@RotaNutri
@ApiResponses({
	@ApiResponse(responseCode="400",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="401",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="403",description="ProblemDetail com código estável (`ACESSO_NEGADO` ao mexer em modelo do sistema)",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="404",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="409",description="Conflito de versão ou idempotência; atual só se autorizado",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="422",description="ProblemDetail com código estável (`DOCUMENTO_JA_ANULADO`, `ASSINATURA_INATIVA`)",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class)))
})
public class DocumentoController {
	private final GerenciarDocumentos documentos;private final GerenciarModelosDocumento modelos;
	public DocumentoController(GerenciarDocumentos documentos,GerenciarModelosDocumento modelos){this.documentos=documentos;this.modelos=modelos;}

	@GetMapping("/eu/nutri/modelos-documento")
	@Operation(operationId="listarModelosDocumento",summary="Modelos de documento",description="RN-DOC-01; os dois do sistema (`doSistema=true`, só leitura) e os da nutri; BE-78.")
	@ApiResponse(responseCode="200",description="Modelos",content=@Content(mediaType="application/json",schema=@Schema(implementation=ModelosDocumentoResposta.class)))
	public ResponseEntity<ModelosDocumentoResposta> listarModelos(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(modelos.listar());}

	@PostMapping("/eu/nutri/modelos-documento")
	@Idempotente
	@Operation(operationId="criarModeloDocumento",summary="Criar modelo de documento",description="RN-DOC-01; para editar um modelo do sistema, a nutri cria um novo com o texto dele. Campos: `{{paciente}}`, `{{data}}`, `{{horario}}`, `{{nutri}}`, `{{crn}}`; BE-78.")
	@ApiResponse(responseCode="201",description="Modelo criado",content=@Content(mediaType="application/json",schema=@Schema(implementation=ModeloDocumentoResposta.class)),headers=@Header(name="ETag",description="Versão forte da entidade",schema=@Schema(type="string")))
	public ResponseEntity<ModeloDocumentoResposta> criarModelo(@RequestHeader(value="Idempotency-Key",required=false) UUID chave,@Valid @RequestBody ModeloDocumentoRequisicao requisicao){
		var m=modelos.criar(requisicao);return ResponseEntity.status(201).eTag(Long.toString(m.versao())).cacheControl(CacheControl.noStore()).body(m);
	}

	@PutMapping("/eu/nutri/modelos-documento/{id}")
	@Operation(operationId="atualizarModeloDocumento",summary="Substituir modelo de documento",description="RN-DOC-01, RN-SYN-02; modelo do sistema: `403 ACESSO_NEGADO`; documentos já emitidos não mudam; BE-78.")
	@ApiResponse(responseCode="200",description="Modelo atualizado",content=@Content(mediaType="application/json",schema=@Schema(implementation=ModeloDocumentoResposta.class)),headers=@Header(name="ETag",description="Nova versão",schema=@Schema(type="string")))
	public ResponseEntity<ModeloDocumentoResposta> atualizarModelo(@PathVariable UUID id,@Parameter(description="Versão entre aspas, por exemplo \"1\"") @RequestHeader("If-Match") String ifMatch,@Valid @RequestBody ModeloDocumentoRequisicao requisicao){
		var m=modelos.atualizar(id,ifMatch,requisicao);return ResponseEntity.ok().eTag(Long.toString(m.versao())).cacheControl(CacheControl.noStore()).body(m);
	}

	@DeleteMapping("/eu/nutri/modelos-documento/{id}")
	@Operation(operationId="excluirModeloDocumento",summary="Apagar modelo de documento",description="RN-DOC-01; modelo do sistema: `403 ACESSO_NEGADO`; documentos já emitidos não mudam; BE-78.")
	@ApiResponse(responseCode="204",description="Apagado")
	public ResponseEntity<Void> excluirModelo(@PathVariable UUID id,@Parameter(description="Versão entre aspas, por exemplo \"1\"") @RequestHeader("If-Match") String ifMatch){
		modelos.excluir(id,ifMatch);return ResponseEntity.noContent().build();
	}

	@GetMapping("/pacientes/{id}/documentos")
	@Operation(operationId="listarDocumentos",summary="Documentos emitidos para o paciente",description="RN-DOC-02; inclui os anulados, mais novos primeiro; BE-78.")
	@ApiResponse(responseCode="200",description="Documentos",content=@Content(mediaType="application/json",schema=@Schema(implementation=DocumentosResposta.class)))
	public ResponseEntity<DocumentosResposta> listar(@PathVariable UUID id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documentos.listar(id));}

	@PostMapping("/pacientes/{id}/documentos")
	@Idempotente
	@Operation(operationId="emitirDocumento",summary="Emitir atestado ou declaração",description="RN-DOC-01 e 02. Com `consultaId` (consulta REALIZADA deste paciente) data e horário vêm dela; sem ela, `data` (padrão: hoje) e `horario`. "
		+"`texto` opcional substitui o do modelo. Campo do texto sem valor: `400 FORMATO_INVALIDO`. O texto final e o PDF ficam gravados e não mudam. Vínculo encerrado só lê; escrita online; BE-78.")
	@ApiResponse(responseCode="201",description="Documento emitido",content=@Content(mediaType="application/json",schema=@Schema(implementation=DocumentoResposta.class)))
	public ResponseEntity<DocumentoResposta> emitir(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) UUID chave,@Valid @RequestBody EmitirDocumentoRequisicao requisicao){
		return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(documentos.emitir(id,requisicao));
	}

	@PostMapping("/documentos/{id}/anular")
	@Operation(operationId="anularDocumento",summary="Anular documento",description="RN-DOC-02; motivo obrigatório, vai para a auditoria; já anulado: `422 DOCUMENTO_JA_ANULADO`. A conferência passa a dizer ANULADO; BE-78.")
	@ApiResponse(responseCode="200",description="Documento anulado",content=@Content(mediaType="application/json",schema=@Schema(implementation=DocumentoResposta.class)))
	public ResponseEntity<DocumentoResposta> anular(@PathVariable UUID id,@Valid @RequestBody AnularDocumentoRequisicao requisicao){
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documentos.anular(id,requisicao));
	}

	@GetMapping("/documentos/{id}/pdf")
	@Operation(operationId="pdfDocumento",summary="PDF do documento emitido",description="RN-DOC-02; link assinado de 10 min para o PDF gravado na emissão; BE-78.")
	@ApiResponse(responseCode="200",description="Link temporário",content=@Content(mediaType="application/json",schema=@Schema(implementation=PdfResposta.class)))
	public ResponseEntity<PdfResposta> pdf(@PathVariable UUID id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documentos.pdf(id));}
}
