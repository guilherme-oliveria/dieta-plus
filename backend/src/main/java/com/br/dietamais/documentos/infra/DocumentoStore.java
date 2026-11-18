package com.br.dietamais.documentos.infra;

import com.br.dietamais.documentos.api.DocumentoPacienteResposta;
import com.br.dietamais.documentos.api.DocumentoResposta;
import com.br.dietamais.documentos.api.TipoDocumento;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Documentos emitidos (RN-DOC-02): texto e PDF nunca mudam; a única alteração é a anulação, com motivo. */
@Repository
public class DocumentoStore {
	private static final String SELECT="select id,paciente_id,tipo,titulo,texto,codigo_conferencia,emitido_em,anulado_em,motivo_anulacao,pdf_chave from documento_emitido ";
	private final JdbcTemplate jdbc;
	public DocumentoStore(JdbcTemplate jdbc){this.jdbc=jdbc;}

	public record Novo(UUID id,UUID vinculo,UUID nutri,UUID paciente,UUID modelo,UUID consulta,TipoDocumento tipo,String titulo,String texto,String emissorNome,String emissorCrn,
		String codigo,String pdfChave,Instant emitidoEm) { }
	public record Documento(DocumentoResposta resposta,String pdfChave) { }

	public void bloquear(UUID nutri){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"documentos:"+nutri);}
	public boolean existe(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from documento_emitido where id=?)",Boolean.class,id));}
	public boolean codigoEmUso(String codigo){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from documento_emitido where codigo_conferencia=?)",Boolean.class,codigo));}
	/** Falso se o id ou o código já existem (o chamador decide se é idempotência ou nova tentativa de código). */
	public boolean inserir(Novo d){
		return jdbc.update("insert into documento_emitido(id,vinculo_id,nutricionista_id,paciente_id,modelo_id,consulta_id,tipo,titulo,texto,emissor_nome,emissor_crn,codigo_conferencia,pdf_chave,emitido_em,criado_em,atualizado_em) "
				+"values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict do nothing",d.id(),d.vinculo(),d.nutri(),d.paciente(),d.modelo(),d.consulta(),d.tipo().name(),d.titulo(),d.texto(),d.emissorNome(),d.emissorCrn(),
				d.codigo(),d.pdfChave(),Timestamp.from(d.emitidoEm()),Timestamp.from(d.emitidoEm()),Timestamp.from(d.emitidoEm()))==1;
	}
	public Documento daNutri(UUID id,UUID nutri,boolean bloquear){
		return jdbc.query(SELECT+"where id=? and nutricionista_id=? and excluido_em is null"+(bloquear?" for update":""),this::mapear,id,nutri).stream().findFirst().orElse(null);
	}
	public List<DocumentoResposta> doPacienteDaNutri(UUID paciente,UUID nutri){
		return jdbc.query(SELECT+"where paciente_id=? and nutricionista_id=? and excluido_em is null order by emitido_em desc,id desc",this::mapear,paciente,nutri).stream().map(Documento::resposta).toList();
	}
	private static final String META="select id,nutricionista_id,paciente_id,tipo,titulo,emitido_em,anulado_em,versao,pdf_chave from documento_emitido ";
	/** Metadados sem texto nem motivo (visão do paciente e coleção `documentos` do sync, BE-79). */
	public record Metadados(DocumentoPacienteResposta resposta,UUID nutri,UUID paciente,String pdfChave) { }
	public Metadados metadadosDaNutri(UUID id,UUID nutri){
		return jdbc.query(META+"where id=? and nutricionista_id=? and excluido_em is null",this::mapearMeta,id,nutri).stream().findFirst().orElse(null);
	}
	/** O paciente lê o que recebeu mesmo com o vínculo encerrado ou a assinatura da nutri inativa (RN-ASS-05). */
	public Metadados metadadosDoPaciente(UUID id,UUID paciente){
		return jdbc.query(META+"where id=? and paciente_id=? and excluido_em is null",this::mapearMeta,id,paciente).stream().findFirst().orElse(null);
	}
	public List<DocumentoPacienteResposta> doPaciente(UUID paciente){
		return jdbc.query(META+"where paciente_id=? and excluido_em is null order by emitido_em desc,id desc",this::mapearMeta,paciente).stream().map(Metadados::resposta).toList();
	}
	/** RN-DOC-03: só o que a conferência pública mostra; nada do paciente. */
	public record Conferencia(UUID id,TipoDocumento tipo,String emissor,String crn,Instant emitidoEm,boolean anulado) { }
	public Conferencia porCodigo(String codigo){
		return jdbc.query("select id,tipo,emissor_nome,emissor_crn,emitido_em,anulado_em from documento_emitido where codigo_conferencia=? and excluido_em is null",
			(rs,n)->new Conferencia(rs.getObject("id",UUID.class),TipoDocumento.valueOf(rs.getString("tipo")),rs.getString("emissor_nome"),rs.getString("emissor_crn"),
				rs.getTimestamp("emitido_em").toInstant(),rs.getTimestamp("anulado_em")!=null),codigo).stream().findFirst().orElse(null);
	}
	public void registrarConferencia(UUID documento,boolean encontrado,String ip,Instant agora){
		jdbc.update("insert into conferencia_documento(documento_id,encontrado,ip,ocorrido_em) values(?,?,?,?)",documento,encontrado,ip,Timestamp.from(agora));
	}
	private Metadados mapearMeta(ResultSet rs,int n) throws SQLException {
		return new Metadados(new DocumentoPacienteResposta(rs.getObject("id",UUID.class),TipoDocumento.valueOf(rs.getString("tipo")),rs.getString("titulo"),
			rs.getTimestamp("emitido_em").toInstant(),rs.getTimestamp("anulado_em")!=null,rs.getLong("versao")),rs.getObject("nutricionista_id",UUID.class),
			rs.getObject("paciente_id",UUID.class),rs.getString("pdf_chave"));
	}

	public void anular(UUID id,UUID nutri,String motivo,Instant agora){
		jdbc.update("update documento_emitido set anulado_em=?,motivo_anulacao=?,versao=versao+1,seq=nextval('seq_sync'),atualizado_em=? where id=? and nutricionista_id=? and anulado_em is null and excluido_em is null",
			Timestamp.from(agora),motivo,Timestamp.from(agora),id,nutri);
	}
	private Documento mapear(ResultSet rs,int n) throws SQLException {
		var anulado=rs.getTimestamp("anulado_em");
		return new Documento(new DocumentoResposta(rs.getObject("id",UUID.class),rs.getObject("paciente_id",UUID.class),TipoDocumento.valueOf(rs.getString("tipo")),rs.getString("titulo"),
			rs.getString("texto"),rs.getString("codigo_conferencia"),rs.getTimestamp("emitido_em").toInstant(),anulado==null?null:anulado.toInstant(),rs.getString("motivo_anulacao")),rs.getString("pdf_chave"));
	}
}
