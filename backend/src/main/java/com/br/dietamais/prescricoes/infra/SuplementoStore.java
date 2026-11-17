package com.br.dietamais.prescricoes.infra;

import com.br.dietamais.prescricoes.api.SuplementoResposta;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Prescrições de suplemento (RN-SUP-01 e 02). Os horários ficam em texto, "08:00,20:00". */
@Repository
public class SuplementoStore {
	private static final String SELECT="select id,paciente_id,produto_id,nome,dose,forma,frequencia,horarios,inicio,fim,versao,criado_em,atualizado_em from prescricao_suplemento ";
	private final JdbcTemplate jdbc;
	public SuplementoStore(JdbcTemplate jdbc){this.jdbc=jdbc;}

	public record Dados(UUID produtoId,String nome,String dose,String forma,String frequencia,List<String> horarios,LocalDate inicio,LocalDate fim) {}

	public void bloquear(UUID nutri){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"suplementos:"+nutri);}

	public boolean inserir(UUID id,UUID vinculo,UUID nutri,UUID paciente,Dados d,Instant agora){
		return jdbc.update("insert into prescricao_suplemento(id,vinculo_id,nutricionista_id,paciente_id,produto_id,nome,dose,forma,frequencia,horarios,inicio,fim,criado_em,atualizado_em) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(id) do nothing",
			id,vinculo,nutri,paciente,d.produtoId(),d.nome(),d.dose(),d.forma(),d.frequencia(),String.join(",",d.horarios()),data(d.inicio()),data(d.fim()),Timestamp.from(agora),Timestamp.from(agora))==1;
	}
	/** Só dose, forma, frequência, horários e fim mudam; nome e produto ficam congelados (RN-SUP-02). */
	public void atualizar(UUID id,UUID nutri,Dados d,Instant agora){
		jdbc.update("update prescricao_suplemento set dose=?,forma=?,frequencia=?,horarios=?,fim=?,versao=versao+1,seq=nextval('seq_sync'),atualizado_em=? where id=? and nutricionista_id=? and excluido_em is null",
			d.dose(),d.forma(),d.frequencia(),String.join(",",d.horarios()),data(d.fim()),Timestamp.from(agora),id,nutri);
	}
	public SuplementoResposta daNutri(UUID id,UUID nutri,boolean bloquear){
		return jdbc.query(SELECT+"where id=? and nutricionista_id=? and excluido_em is null"+(bloquear?" for update":""),(rs,n)->mapear(rs,true),id,nutri).stream().findFirst().orElse(null);
	}
	public List<SuplementoResposta> doPacienteDaNutri(UUID paciente,UUID nutri){
		return jdbc.query(SELECT+"where paciente_id=? and nutricionista_id=? and excluido_em is null order by inicio desc,criado_em,id",(rs,n)->mapear(rs,true),paciente,nutri);
	}
	/** Projeção do paciente: sem `produtoId` (não vê o catálogo da nutri, C-08), mesmo com o vínculo encerrado. */
	public SuplementoResposta doPaciente(UUID id,UUID paciente){
		return jdbc.query(SELECT+"where id=? and paciente_id=? and excluido_em is null",(rs,n)->mapear(rs,false),id,paciente).stream().findFirst().orElse(null);
	}
	private static java.sql.Date data(LocalDate d){return d==null?null:java.sql.Date.valueOf(d);}
	private SuplementoResposta mapear(ResultSet rs,boolean comProduto) throws SQLException {
		var h=rs.getString("horarios");
		return new SuplementoResposta(rs.getObject("id",UUID.class),rs.getObject("paciente_id",UUID.class),comProduto?rs.getObject("produto_id",UUID.class):null,rs.getString("nome"),rs.getString("dose"),
			rs.getString("forma"),rs.getString("frequencia"),h==null||h.isEmpty()?List.of():List.of(h.split(",")),rs.getObject("inicio",LocalDate.class),rs.getObject("fim",LocalDate.class),
			rs.getLong("versao"),rs.getTimestamp("criado_em").toInstant(),rs.getTimestamp("atualizado_em").toInstant());
	}
}
