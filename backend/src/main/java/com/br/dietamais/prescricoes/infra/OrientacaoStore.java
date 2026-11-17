package com.br.dietamais.prescricoes.infra;

import com.br.dietamais.prescricoes.api.OrientacaoResposta;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Orientações avulsas por paciente (RN-ORI-01). Arquivar marca `arquivada_em`; nada é apagado. */
@Repository
public class OrientacaoStore {
	private static final String SELECT="select id,paciente_id,titulo,texto,arquivada_em,versao,criado_em,atualizado_em from orientacao ";
	private final JdbcTemplate jdbc;
	public OrientacaoStore(JdbcTemplate jdbc){this.jdbc=jdbc;}

	public void bloquear(UUID nutri){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"orientacoes:"+nutri);}
	public boolean inserir(UUID id,UUID vinculo,UUID nutri,UUID paciente,String titulo,String texto,Instant agora){
		return jdbc.update("insert into orientacao(id,vinculo_id,nutricionista_id,paciente_id,titulo,texto,criado_em,atualizado_em) values(?,?,?,?,?,?,?,?) on conflict(id) do nothing",
			id,vinculo,nutri,paciente,titulo,texto,Timestamp.from(agora),Timestamp.from(agora))==1;
	}
	public void atualizar(UUID id,UUID nutri,String titulo,String texto,boolean arquivar,Instant agora){
		jdbc.update("update orientacao set titulo=?,texto=?,arquivada_em=case when ? then coalesce(arquivada_em,?) else arquivada_em end,versao=versao+1,seq=nextval('seq_sync'),atualizado_em=? where id=? and nutricionista_id=? and excluido_em is null",
			titulo,texto,arquivar,Timestamp.from(agora),Timestamp.from(agora),id,nutri);
	}
	public OrientacaoResposta daNutri(UUID id,UUID nutri,boolean bloquear){
		return jdbc.query(SELECT+"where id=? and nutricionista_id=? and excluido_em is null"+(bloquear?" for update":""),this::mapear,id,nutri).stream().findFirst().orElse(null);
	}
	public List<OrientacaoResposta> doPacienteDaNutri(UUID paciente,UUID nutri){
		return jdbc.query(SELECT+"where paciente_id=? and nutricionista_id=? and excluido_em is null order by criado_em desc,id",this::mapear,paciente,nutri);
	}
	/** O paciente lê o que recebeu mesmo com o vínculo encerrado ou a assinatura da nutri inativa (RN-ASS-05). */
	public OrientacaoResposta doPaciente(UUID id,UUID paciente){
		return jdbc.query(SELECT+"where id=? and paciente_id=? and excluido_em is null",this::mapear,id,paciente).stream().findFirst().orElse(null);
	}
	private OrientacaoResposta mapear(ResultSet rs,int n) throws SQLException {
		var arq=rs.getTimestamp("arquivada_em");
		return new OrientacaoResposta(rs.getObject("id",UUID.class),rs.getObject("paciente_id",UUID.class),rs.getString("titulo"),rs.getString("texto"),arq==null?null:arq.toInstant(),
			rs.getLong("versao"),rs.getTimestamp("criado_em").toInstant(),rs.getTimestamp("atualizado_em").toInstant());
	}
}
