package com.br.dietamais.documentos.infra;

import com.br.dietamais.documentos.api.ModeloDocumentoResposta;
import com.br.dietamais.documentos.api.TipoDocumento;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Modelos de documento (RN-DOC-01): os do sistema têm {@code nutricionista_id} nulo; os da nutri ficam isolados por nutri. */
@Repository
public class ModeloDocumentoStore {
	private static final String SELECT="select id,nutricionista_id,tipo,nome,texto,versao,criado_em,atualizado_em from modelo_documento ";
	private static final String VISIVEL="where (nutricionista_id is null or nutricionista_id=?) and excluido_em is null ";
	private final JdbcTemplate jdbc;
	public ModeloDocumentoStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
	public void bloquear(UUID nutri){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"modelos-documento:"+nutri);}
	/** Os do sistema primeiro, depois os da nutri por nome. */
	public List<ModeloDocumentoResposta> visiveis(UUID nutri){
		return jdbc.query(SELECT+VISIVEL+"order by (nutricionista_id is not null),nome,id",this::mapear,nutri);
	}
	/** Modelo do sistema ou da própria nutri; nulo se for de outra nutri, apagado ou inexistente. */
	public ModeloDocumentoResposta visivel(UUID id,UUID nutri,boolean bloquear){
		return jdbc.query(SELECT+VISIVEL+"and id=?"+(bloquear?" for update":""),this::mapear,nutri,id).stream().findFirst().orElse(null);
	}
	public int contar(UUID nutri){return jdbc.queryForObject("select count(*) from modelo_documento where nutricionista_id=? and excluido_em is null",Integer.class,nutri);}
	public void inserir(UUID id,UUID nutri,TipoDocumento tipo,String nome,String texto,Instant agora){
		jdbc.update("insert into modelo_documento(id,nutricionista_id,tipo,nome,texto,criado_em,atualizado_em) values(?,?,?,?,?,?,?)",id,nutri,tipo.name(),nome,texto,Timestamp.from(agora),Timestamp.from(agora));
	}
	public boolean atualizar(UUID id,UUID nutri,long versao,TipoDocumento tipo,String nome,String texto,Instant agora){
		return jdbc.update("update modelo_documento set tipo=?,nome=?,texto=?,versao=versao+1,atualizado_em=? where id=? and nutricionista_id=? and versao=? and excluido_em is null",
			tipo.name(),nome,texto,Timestamp.from(agora),id,nutri,versao)==1;
	}
	public boolean excluir(UUID id,UUID nutri,long versao,Instant agora){
		return jdbc.update("update modelo_documento set excluido_em=?,versao=versao+1,atualizado_em=? where id=? and nutricionista_id=? and versao=? and excluido_em is null",
			Timestamp.from(agora),Timestamp.from(agora),id,nutri,versao)==1;
	}
	private ModeloDocumentoResposta mapear(ResultSet rs,int n) throws SQLException {
		return new ModeloDocumentoResposta(rs.getObject("id",UUID.class),TipoDocumento.valueOf(rs.getString("tipo")),rs.getString("nome"),rs.getString("texto"),
			rs.getObject("nutricionista_id")==null,rs.getLong("versao"),rs.getTimestamp("criado_em").toInstant(),rs.getTimestamp("atualizado_em").toInstant());
	}
}
