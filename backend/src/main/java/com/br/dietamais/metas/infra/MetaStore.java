package com.br.dietamais.metas.infra;

import com.br.dietamais.metas.api.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MetaStore {
	private static final String SELECT="select id,paciente_id,vinculo_id,tipo,nome,alvo,unidade,frequencia,vezes_por_semana,inicio,fim,versao,criado_em,atualizado_em from meta_habito ";
	private final JdbcTemplate jdbc;
	public MetaStore(JdbcTemplate jdbc){this.jdbc=jdbc;}

	public record Dados(TipoMeta tipo,String nome,BigDecimal alvo,String unidade,FrequenciaMeta frequencia,Integer vezesPorSemana,LocalDate inicio,LocalDate fim) {}

	public void bloquear(UUID nutri){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"metas:"+nutri);}

	public boolean inserir(UUID id,UUID vinculo,UUID nutri,UUID paciente,Dados d,Instant agora){
		return jdbc.update("insert into meta_habito(id,vinculo_id,nutricionista_id,paciente_id,tipo,nome,alvo,unidade,frequencia,vezes_por_semana,inicio,fim,criado_em,atualizado_em) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(id) do nothing",
			id,vinculo,nutri,paciente,d.tipo().name(),d.nome(),d.alvo(),d.unidade(),d.frequencia().name(),d.vezesPorSemana(),data(d.inicio()),data(d.fim()),Timestamp.from(agora),Timestamp.from(agora))==1;
	}
	public void atualizar(UUID id,UUID nutri,Dados d,Instant agora){
		jdbc.update("update meta_habito set nome=?,alvo=?,unidade=?,frequencia=?,vezes_por_semana=?,inicio=?,fim=?,versao=versao+1,seq=nextval('seq_sync'),atualizado_em=? where id=? and nutricionista_id=? and excluido_em is null",
			d.nome(),d.alvo(),d.unidade(),d.frequencia().name(),d.vezesPorSemana(),data(d.inicio()),data(d.fim()),Timestamp.from(agora),id,nutri);
	}
	public MetaResposta daNutri(UUID id,UUID nutri,boolean bloquear){
		return jdbc.query(SELECT+"where id=? and nutricionista_id=? and excluido_em is null"+(bloquear?" for update":""),this::mapear,id,nutri).stream().findFirst().orElse(null);
	}
	/** Meta do paciente em vínculo ativo ou encerrado (o paciente só lê); histórico autorizado. */
	public MetaResposta doPaciente(UUID id,UUID paciente){
		return jdbc.query(SELECT+"where id=? and paciente_id=? and excluido_em is null",this::mapear,id,paciente).stream().findFirst().orElse(null);
	}
	/** Meta que o paciente pode registrar: do vínculo ATIVO dele. */
	public MetaResposta doPacienteAtiva(UUID id,UUID paciente){
		return jdbc.query(SELECT+"m where m.id=? and m.paciente_id=? and m.excluido_em is null and exists(select 1 from vinculo v where v.id=m.vinculo_id and v.status='ATIVO' and v.excluido_em is null)",this::mapear,id,paciente).stream().findFirst().orElse(null);
	}
	public List<MetaResposta> doPacienteDaNutri(UUID paciente,UUID nutri){
		return jdbc.query(SELECT+"where paciente_id=? and nutricionista_id=? and excluido_em is null order by criado_em,id",this::mapear,paciente,nutri);
	}
	/** Metas que se sobrepõem à janela [de, ate]. */
	public List<MetaResposta> daJanela(UUID paciente,UUID nutri,LocalDate de,LocalDate ate){
		return jdbc.query(SELECT+"where paciente_id=? and nutricionista_id=? and excluido_em is null and (inicio is null or inicio<=?) and (fim is null or fim>=?) order by criado_em,id",
			this::mapear,paciente,nutri,data(ate),data(de));
	}

	public boolean inserirRegistro(UUID id,UUID meta,UUID paciente,LocalDate data,BigDecimal valor,Instant registradoEm){
		return jdbc.update("insert into registro_meta(id,meta_id,paciente_id,data,valor,registrado_em) values(?,?,?,?,?,?) on conflict(id) do nothing",id,meta,paciente,data(data),valor,Timestamp.from(registradoEm))==1;
	}
	public RegistroMetaResposta registro(UUID id,UUID paciente){
		return jdbc.query("select * from registro_meta where id=? and paciente_id=?",(rs,n)->registro(rs),id,paciente).stream().findFirst().orElse(null);
	}
	public RegistroMetaResposta registroDoPacienteDaNutri(UUID id,UUID nutri){
		return jdbc.query("select r.* from registro_meta r where r.id=? and exists(select 1 from vinculo v where v.paciente_id=r.paciente_id and v.nutricionista_id=? and v.status='ATIVO' and v.excluido_em is null)",(rs,n)->registro(rs),id,nutri).stream().findFirst().orElse(null);
	}
	/** Soma por dia dos registros de cada meta na janela (registros do mesmo dia somam, RN-MET-03). */
	public Map<UUID,Map<LocalDate,BigDecimal>> somasPorDia(Collection<UUID> metas,LocalDate de,LocalDate ate){
		var r=new HashMap<UUID,Map<LocalDate,BigDecimal>>();
		if(metas.isEmpty())return r;
		jdbc.query("select meta_id,data,sum(valor) from registro_meta where meta_id=any(?::uuid[]) and data between ? and ? group by meta_id,data",
			(rs)->{r.computeIfAbsent(rs.getObject(1,UUID.class),k->new HashMap<>()).put(rs.getObject(2,LocalDate.class),rs.getBigDecimal(3));},(Object)metas.toArray(UUID[]::new),data(de),data(ate));
		return r;
	}
	private static RegistroMetaResposta registro(ResultSet rs) throws SQLException {
		return new RegistroMetaResposta(rs.getObject("id",UUID.class),rs.getObject("meta_id",UUID.class),rs.getObject("data",LocalDate.class),rs.getBigDecimal("valor"),rs.getTimestamp("registrado_em").toInstant());
	}
	private static java.sql.Date data(LocalDate d){return d==null?null:java.sql.Date.valueOf(d);}
	private MetaResposta mapear(ResultSet rs,int n) throws SQLException {
		var tipo=TipoMeta.valueOf(rs.getString("tipo"));
		return new MetaResposta(rs.getObject("id",UUID.class),rs.getObject("paciente_id",UUID.class),tipo,rs.getString("nome"),rs.getBigDecimal("alvo"),rs.getString("unidade"),
			FrequenciaMeta.valueOf(rs.getString("frequencia")),rs.getObject("vezes_por_semana",Integer.class),rs.getObject("inicio",LocalDate.class),rs.getObject("fim",LocalDate.class),
			null,rs.getLong("versao"),rs.getTimestamp("criado_em").toInstant(),rs.getTimestamp("atualizado_em").toInstant());
	}
}
