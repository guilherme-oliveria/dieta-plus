package com.br.dietamais.prescricoes.infra;

import com.br.dietamais.prescricoes.api.ModeloOrientacaoResposta;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Modelos de orientação da nutri (RN-ORI-01), isolados por nutri. */
@Repository
public class ModeloOrientacaoStore {
	private static final String BASE="select id,titulo,texto,versao,criado_em,atualizado_em from modelo_orientacao where nutricionista_id=? and excluido_em is null";
	private final JdbcTemplate jdbc;
	public ModeloOrientacaoStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
	public void bloquear(UUID nutri){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"modelos-orientacao:"+nutri);}
	public List<ModeloOrientacaoResposta> daNutri(UUID nutri){return jdbc.query(BASE+" order by titulo,id",this::mapear,nutri);}
	public ModeloOrientacaoResposta daNutri(UUID id,UUID nutri,boolean bloquear){
		return jdbc.query(BASE+" and id=?"+(bloquear?" for update":""),this::mapear,nutri,id).stream().findFirst().orElse(null);
	}
	public int contar(UUID nutri){return jdbc.queryForObject("select count(*) from modelo_orientacao where nutricionista_id=? and excluido_em is null",Integer.class,nutri);}
	public void inserir(UUID id,UUID nutri,String titulo,String texto,Instant agora){
		jdbc.update("insert into modelo_orientacao(id,nutricionista_id,titulo,texto,criado_em,atualizado_em) values(?,?,?,?,?,?)",id,nutri,titulo,texto,Timestamp.from(agora),Timestamp.from(agora));
	}
	public boolean atualizar(UUID id,UUID nutri,long versao,String titulo,String texto,Instant agora){
		return jdbc.update("update modelo_orientacao set titulo=?,texto=?,versao=versao+1,atualizado_em=? where id=? and nutricionista_id=? and versao=? and excluido_em is null",titulo,texto,Timestamp.from(agora),id,nutri,versao)==1;
	}
	public boolean excluir(UUID id,UUID nutri,long versao,Instant agora){
		return jdbc.update("update modelo_orientacao set excluido_em=?,versao=versao+1,atualizado_em=? where id=? and nutricionista_id=? and versao=? and excluido_em is null",Timestamp.from(agora),Timestamp.from(agora),id,nutri,versao)==1;
	}
	private ModeloOrientacaoResposta mapear(ResultSet rs,int n) throws SQLException {
		return new ModeloOrientacaoResposta(rs.getObject("id",UUID.class),rs.getString("titulo"),rs.getString("texto"),rs.getLong("versao"),rs.getTimestamp("criado_em").toInstant(),rs.getTimestamp("atualizado_em").toInstant());
	}
}
