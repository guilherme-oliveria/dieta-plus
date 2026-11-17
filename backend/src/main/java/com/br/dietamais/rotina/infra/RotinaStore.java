package com.br.dietamais.rotina.infra;

import com.br.dietamais.rotina.api.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class RotinaStore {
	private final JdbcTemplate jdbc;private final JsonMapper mapper;
	public RotinaStore(JdbcTemplate jdbc,JsonMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
	public boolean inserirCheckin(UUID id,UUID paciente,UUID vinculo,UUID nutri,CheckinRequisicao r,List<TrocaCheckin> trocas){
		return jdbc.update("""
			insert into checkin(id,paciente_id,vinculo_id,nutricionista_id,dieta_id,refeicao_id,data,status,trocas,registrado_em,fome_antes,sensacao_depois) values(?,?,?,?,?,?,?,?,?::jsonb,?,?,?) on conflict(id) do nothing
			""",id,paciente,vinculo,nutri,r.dietaId(),r.refeicaoId(),java.sql.Date.valueOf(r.data()),r.status().name(),mapper.writeValueAsString(trocas),Timestamp.from(r.registradoEm()),r.fomeAntes(),r.sensacaoDepois())==1;
	}
	public CheckinResposta checkin(UUID id,UUID paciente){
		return jdbc.query("select * from checkin where id=? and paciente_id=?",(rs,n)->checkin(rs),id,paciente).stream().findFirst().orElse(null);
	}
	private CheckinResposta checkin(ResultSet rs) throws SQLException {
		return new CheckinResposta(rs.getObject("id",UUID.class),rs.getObject("paciente_id",UUID.class),rs.getObject("dieta_id",UUID.class),
			rs.getObject("refeicao_id",UUID.class),rs.getObject("data",LocalDate.class),StatusCheckin.valueOf(rs.getString("status")),
			mapper.readValue(rs.getString("trocas"),new TypeReference<List<TrocaCheckin>>(){}),rs.getTimestamp("registrado_em").toInstant(),
			rs.getObject("fome_antes",Integer.class),rs.getObject("sensacao_depois",Integer.class));
	}
	public boolean existe(String tabela,UUID id){return jdbc.queryForObject("select count(*) from "+tabela+" where id=?",Integer.class,id)>0;}
	public boolean inserirAgua(UUID id,UUID paciente,AguaRequisicao r){
		return jdbc.update("insert into registro_agua(id,paciente_id,ml,registrado_em) values(?,?,?,?) on conflict(id) do nothing",id,paciente,r.ml(),Timestamp.from(r.registradoEm()))==1;
	}
	public AguaResposta agua(UUID id,UUID paciente){
		return jdbc.query("select * from registro_agua where id=? and paciente_id=?",(rs,n)->new AguaResposta(rs.getObject("id",UUID.class),rs.getObject("paciente_id",UUID.class),rs.getInt("ml"),rs.getTimestamp("registrado_em").toInstant()),id,paciente).stream().findFirst().orElse(null);
	}
	public boolean inserirPeso(UUID id,UUID paciente,BigDecimal peso,Instant registradoEm){
		return jdbc.update("insert into registro_peso(id,paciente_id,peso_kg,registrado_em) values(?,?,?,?) on conflict(id) do nothing",id,paciente,peso,Timestamp.from(registradoEm))==1;
	}
	public PesoResposta peso(UUID id,UUID paciente){
		return jdbc.query("select * from registro_peso where id=? and paciente_id=?",(rs,n)->new PesoResposta(rs.getObject("id",UUID.class),rs.getObject("paciente_id",UUID.class),rs.getBigDecimal("peso_kg"),rs.getTimestamp("registrado_em").toInstant()),id,paciente).stream().findFirst().orElse(null);
	}
	/** Vínculo ativo do paciente com a nutri dona do snapshot (id da nutri vem do snapshot, nunca do cliente). */
	public UUID vinculoAtivo(UUID vinculo,UUID paciente){
		return jdbc.query("select id from vinculo where id=? and paciente_id=? and status='ATIVO' and excluido_em is null",(rs,n)->rs.getObject(1,UUID.class),vinculo,paciente).stream().findFirst().orElse(null);
	}
	public CheckinResposta checkinDoVinculo(UUID id,UUID nutri){
		return jdbc.query("select * from checkin where id=? and nutricionista_id=?",(rs,n)->checkin(rs),id,nutri).stream().findFirst().orElse(null);
	}
	public PesoResposta pesoDoPacienteDaNutri(UUID id,UUID nutri){
		return jdbc.query("""
			select p.* from registro_peso p where p.id=? and exists(select 1 from vinculo v where v.paciente_id=p.paciente_id and v.nutricionista_id=? and v.status='ATIVO' and v.excluido_em is null)
			""",(rs,n)->new PesoResposta(rs.getObject("id",UUID.class),rs.getObject("paciente_id",UUID.class),rs.getBigDecimal("peso_kg"),rs.getTimestamp("registrado_em").toInstant()),id,nutri).stream().findFirst().orElse(null);
	}
	public List<CheckinResposta> checkinsDoPeriodo(UUID paciente,LocalDate de,LocalDate ate){
		return jdbc.query("select * from checkin where paciente_id=? and data between ? and ? order by registrado_em,id",(rs,n)->checkin(rs),paciente,java.sql.Date.valueOf(de),java.sql.Date.valueOf(ate));
	}
	/** Janela semiaberta [inicio, fim). */
	public List<AguaResposta> aguaDoPeriodo(UUID paciente,Instant inicio,Instant fim){
		return jdbc.query("select * from registro_agua where paciente_id=? and registrado_em>=? and registrado_em<? order by registrado_em,id",(rs,n)->new AguaResposta(rs.getObject("id",UUID.class),rs.getObject("paciente_id",UUID.class),rs.getInt("ml"),rs.getTimestamp("registrado_em").toInstant()),paciente,Timestamp.from(inicio),Timestamp.from(fim));
	}
	public List<PesoResposta> pesosDoPeriodo(UUID paciente,Instant inicio,Instant fim){
		return jdbc.query("select * from registro_peso where paciente_id=? and registrado_em>=? and registrado_em<? order by registrado_em,id",(rs,n)->new PesoResposta(rs.getObject("id",UUID.class),rs.getObject("paciente_id",UUID.class),rs.getBigDecimal("peso_kg"),rs.getTimestamp("registrado_em").toInstant()),paciente,Timestamp.from(inicio),Timestamp.from(fim));
	}
	/** Os registros mais recentes do paciente, devolvidos do mais antigo para o mais novo. */
	public java.util.List<com.br.dietamais.rotina.api.PesosEmCasa.Ponto> pesosDoPaciente(UUID paciente,int limite){
		var l=new java.util.ArrayList<>(jdbc.query("select registrado_em,peso_kg from registro_peso where paciente_id=? order by registrado_em desc,id desc limit ?",
			(rs,n)->new com.br.dietamais.rotina.api.PesosEmCasa.Ponto(rs.getTimestamp(1).toInstant(),rs.getBigDecimal(2)),paciente,limite));
		java.util.Collections.reverse(l);return l;
	}

	// RN-ROT-06: registros "comi fora do plano" (só inclusão)
	public boolean inserirForaDoPlano(UUID id,UUID paciente,RegistroForaDoPlanoRequisicao r,String texto){
		return jdbc.update("insert into registro_fora_do_plano(id,paciente_id,data,texto,refeicao_id,registrado_em) values(?,?,?,?,?,?) on conflict(id) do nothing",
			id,paciente,java.sql.Date.valueOf(r.data()),texto,r.refeicaoId(),Timestamp.from(r.registradoEm()))==1;
	}
	private static RegistroForaDoPlanoResposta foraDoPlano(ResultSet rs) throws SQLException {
		return new RegistroForaDoPlanoResposta(rs.getObject("id",UUID.class),rs.getObject("data",LocalDate.class),rs.getString("texto"),rs.getObject("refeicao_id",UUID.class),rs.getTimestamp("registrado_em").toInstant());
	}
	public RegistroForaDoPlanoResposta foraDoPlano(UUID id,UUID paciente){
		return jdbc.query("select * from registro_fora_do_plano where id=? and paciente_id=?",(rs,n)->foraDoPlano(rs),id,paciente).stream().findFirst().orElse(null);
	}
	public RegistroForaDoPlanoResposta foraDoPlanoDoPacienteDaNutri(UUID id,UUID nutri){
		return jdbc.query("""
			select f.* from registro_fora_do_plano f where f.id=? and exists(select 1 from vinculo v where v.paciente_id=f.paciente_id and v.nutricionista_id=? and v.status='ATIVO' and v.excluido_em is null)
			""",(rs,n)->foraDoPlano(rs),id,nutri).stream().findFirst().orElse(null);
	}
	public List<RegistroForaDoPlanoResposta> foraDoPlanoDoPeriodo(UUID paciente,LocalDate de,LocalDate ate){
		return jdbc.query("select * from registro_fora_do_plano where paciente_id=? and data between ? and ? order by registrado_em desc,id desc",(rs,n)->foraDoPlano(rs),paciente,java.sql.Date.valueOf(de),java.sql.Date.valueOf(ate));
	}
}
