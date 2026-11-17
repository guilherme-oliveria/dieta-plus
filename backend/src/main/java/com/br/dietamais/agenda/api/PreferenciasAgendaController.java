package com.br.dietamais.agenda.api;

import com.br.dietamais.agenda.aplicacao.GerenciarPreferenciasAgenda;
import com.br.dietamais.comum.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/eu/nutri/preferencias-agenda")
@Tag(name="agenda")
@SecurityRequirement(name="bearerAuth")
@RotaNutri
@ApiResponses({
	@ApiResponse(responseCode="400",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="401",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class))),
	@ApiResponse(responseCode="403",description="ProblemDetail com código estável",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemaResposta.class)))
})
public class PreferenciasAgendaController {
	private final GerenciarPreferenciasAgenda preferencias;
	public PreferenciasAgendaController(GerenciarPreferenciasAgenda preferencias){this.preferencias=preferencias;}

	@GetMapping
	@Operation(operationId="consultarPreferenciasAgenda",summary="Preferência de lembrete de consulta",description="BE-72, RN-AGE-05; sem nada salvo devolve o padrão (UM_DIA, sem lembrete extra).")
	@ApiResponse(responseCode="200",description="Preferência atual",content=@Content(mediaType="application/json",schema=@Schema(implementation=PreferenciasAgendaResposta.class)))
	public ResponseEntity<PreferenciasAgendaResposta> consultarPreferenciasAgenda(){
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(preferencias.consultar());
	}

	@PutMapping
	@Operation(operationId="salvarPreferenciasAgenda",summary="Salvar preferência de lembrete de consulta",description="BE-72, RN-AGE-05, D-08; a antecedência vale para o lembrete da nutri e dos pacientes dela; cada um ainda desliga o tipo (RN-NOT-03). Sem ETag: o último vale.")
	@ApiResponse(responseCode="200",description="Preferência salva",content=@Content(mediaType="application/json",schema=@Schema(implementation=PreferenciasAgendaResposta.class)))
	public ResponseEntity<PreferenciasAgendaResposta> salvarPreferenciasAgenda(@Valid @RequestBody PreferenciasAgendaRequisicao requisicao){
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(preferencias.salvar(requisicao));
	}
}
