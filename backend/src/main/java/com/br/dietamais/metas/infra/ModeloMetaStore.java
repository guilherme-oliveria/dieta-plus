package com.br.dietamais.metas.infra;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Modelos de metas da nutri (RN-MET-04). Os itens ficam em jsonb já serializado. */
@Repository
public class ModeloMetaStore {
	private static final String BASE="select id,nome,itens::text itens,versao,criado_em,atualizado_em from modelo_meta where nutricionista_id=? and excluido_em is null";
	private final JdbcTemplate jdbc;
	public ModeloMetaStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
	public record Linha(UUID id,String nome,String itensJson,long versao,Instant criadoEm,Instant atualizadoEm) {}

	public void bloquear(UUID nutri){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"modelos-meta:"+nutri);}
	public List<Linha> daNutri(UUID nutri){return jdbc.query(BASE+" order by nome,id",this::mapear,nutri);}
	public Linha daNutri(UUID id,UUID nutri,boolean bloquear){
		return jdbc.query(BASE+" and id=?"+(bloquear?" for update":""),this::mapear,nutri,id).stream().findFirst().orElse(null);
	}
	public int contar(UUID nutri){return jdbc.queryForObject("select count(*) from modelo_meta where nutricionista_id=? and excluido_em is null",Integer.class,nutri);}
	public boolean inserir(UUID id,UUID nutri,String nome,String itensJson,Instant agora){
		return jdbc.update("insert into modelo_meta(id,nutricionista_id,nome,itens,criado_em,atualizado_em) values(?,?,?,?::jsonb,?,?) on conflict(id) do nothing",
			id,nutri,nome,itensJson,Timestamp.from(agora),Timestamp.from(agora))==1;
	}
	public boolean atualizar(UUID id,UUID nutri,long versao,String nome,String itensJson,Instant agora){
		return jdbc.update("update modelo_meta set nome=?,itens=?::jsonb,versao=versao+1,atualizado_em=? where id=? and nutricionista_id=? and versao=? and excluido_em is null",
			nome,itensJson,Timestamp.from(agora),id,nutri,versao)==1;
	}
	public boolean excluir(UUID id,UUID nutri,long versao,Instant agora){
		return jdbc.update("update modelo_meta set excluido_em=?,versao=versao+1,atualizado_em=? where id=? and nutricionista_id=? and versao=? and excluido_em is null",
			Timestamp.from(agora),Timestamp.from(agora),id,nutri,versao)==1;
	}
	private Linha mapear(ResultSet rs,int n) throws SQLException {
		return new Linha(rs.getObject("id",UUID.class),rs.getString("nome"),rs.getString("itens"),rs.getLong("versao"),rs.getTimestamp("criado_em").toInstant(),rs.getTimestamp("atualizado_em").toInstant());
	}
}
