package com.br.dietamais.documentos.api;

import com.br.dietamais.comum.api.*;
import com.br.dietamais.documentos.aplicacao.DocumentosPaciente;
import com.br.dietamais.relatorios.api.PdfResposta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/eu")
@Tag(name="documentos")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({
	@ApiResponse(responseCode="401",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="403",description="ProblemDetail com código estável (`PERFIL_INVALIDO` para quem não é paciente)",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="404",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class)))
})
public class DocumentoPacienteController {
	private final DocumentosPaciente documentos;
	public DocumentoPacienteController(DocumentosPaciente documentos){this.documentos=documentos;}

	@GetMapping("/documentos")
	@Operation(operationId="listarMeusDocumentos",summary="Documentos que recebi",description="RN-DOC-02; só metadados (tipo, título, data, anulado), sem o texto; inclui anulados e vale com vínculo encerrado; BE-79.")
	@ApiResponse(responseCode="200",description="Documentos",content=@Content(mediaType="application/json",schema=@Schema(implementation=DocumentosPacienteResposta.class)))
	public ResponseEntity<DocumentosPacienteResposta> listar(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documentos.listar());}

	@GetMapping("/documentos/{id}/pdf")
	@Operation(operationId="pdfMeuDocumento",summary="PDF de um documento que recebi",description="RN-DOC-02; link assinado de 10 min para o PDF gravado na emissão; de outro paciente: 404; BE-79.")
	@ApiResponse(responseCode="200",description="Link temporário",content=@Content(mediaType="application/json",schema=@Schema(implementation=PdfResposta.class)))
	public ResponseEntity<PdfResposta> pdf(@PathVariable UUID id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documentos.pdf(id));}
}
