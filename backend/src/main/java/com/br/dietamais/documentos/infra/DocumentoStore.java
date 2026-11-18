package com.br.dietamais.documentos.infra;

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
