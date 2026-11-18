package com.br.dietamais.agenda;

import static org.assertj.core.api.Assertions.*;
import com.br.dietamais.agenda.aplicacao.LembretesConsulta;
import com.br.dietamais.dietas.TesteComPaciente;
import com.br.dietamais.notificacoes.api.*;
import com.br.dietamais.notificacoes.infra.CifradorEmail;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

/** BE-72: preferência de lembrete da nutri (RN-AGE-05, D-08), lembretes pela antecedência e e-mail de confirmação ao agendar. */
class PreferenciasAgendaApiTest extends TesteComPaciente {
	@Autowired LembretesConsulta lembretes;@Autowired CifradorEmail cifra;@Autowired JsonMapper mapper;

	Instant em(long minutos){return Instant.now().plus(minutos,ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);}
	String agendar(ContaTeste a,PacienteTeste p,Instant inicio,Map<String,Object> extra) throws Exception {
		var corpo=new HashMap<String,Object>(Map.of("pacienteId",p.fichaId(),"inicio",inicio.toString(),"duracaoMin",30,"tipo","RETORNO"));corpo.putAll(extra);
		var r=requisitar("POST","/consultas",a.token(),corpo);assertThat(r.statusCode()).as(r.body()).isEqualTo(201);return json(r).path("id").asString();
	}
	int avisos(PacienteTeste p,String consulta){
		return jdbc.queryForObject("select count(*) from aviso where usuario_id=?::uuid and tipo='CONSULTA_AMANHA' and chave_dedup like ?",Integer.class,p.usuarioId(),"consulta:"+consulta+":%");
	}
	List<EmailTransacional> confirmacoes(){
		return jdbc.query("select id,tipo,payload_cifrado from notificacao_pendente where tipo='CONSULTA_AGENDADA' and status='PENDENTE'",
			(rs,i)->mapper.readValue(cifra.decifrar(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),EmailTransacional.class));
	}

	@Test void preferenciaTemPadraoSalvaEValidaPerfil() throws Exception {
		var a=cadastrarNutri();var b=cadastrarNutri();var p=pacienteVinculado(a);
		var padrao=json(requisitar("GET","/eu/nutri/preferencias-agenda",a.token(),null));
		assertThat(padrao.path("antecedencia").asString()).isEqualTo("UM_DIA");assertThat(padrao.path("lembreteExtraUmaHora").asBoolean()).isFalse();
		var salva=requisitar("PUT","/eu/nutri/preferencias-agenda",a.token(),Map.of("antecedencia","DUAS_HORAS","lembreteExtraUmaHora",true));
		assertThat(salva.statusCode()).as(salva.body()).isEqualTo(200);assertThat(json(salva).path("antecedencia").asString()).isEqualTo("DUAS_HORAS");
		var lida=json(requisitar("GET","/eu/nutri/preferencias-agenda",a.token(),null));
		assertThat(lida.path("antecedencia").asString()).isEqualTo("DUAS_HORAS");assertThat(lida.path("lembreteExtraUmaHora").asBoolean()).isTrue();
		// o último vale; cada nutri tem a sua
		requisitar("PUT","/eu/nutri/preferencias-agenda",a.token(),Map.of("antecedencia","DOIS_DIAS","lembreteExtraUmaHora",false));
		assertThat(json(requisitar("GET","/eu/nutri/preferencias-agenda",a.token(),null)).path("antecedencia").asString()).isEqualTo("DOIS_DIAS");
		assertThat(json(requisitar("GET","/eu/nutri/preferencias-agenda",b.token(),null)).path("antecedencia").asString()).isEqualTo("UM_DIA");
		// validação e perfil
		erro(requisitar("PUT","/eu/nutri/preferencias-agenda",a.token(),Map.of("antecedencia","UMA_SEMANA","lembreteExtraUmaHora",false)),400,"FORMATO_INVALIDO");
		var semExtra=new HashMap<String,Object>(Map.of("antecedencia","UM_DIA"));
		assertThat(requisitar("PUT","/eu/nutri/preferencias-agenda",a.token(),semExtra).statusCode()).isEqualTo(400);
		assertThat(requisitar("GET","/eu/nutri/preferencias-agenda",p.token(),null).statusCode()).isEqualTo(403);
		assertThat(requisitar("GET","/eu/nutri/preferencias-agenda",null,null).statusCode()).isEqualTo(401);
	}

	@Test void lembreteSegueAAntecedenciaDaNutriParaOsDoisLados() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);
		requisitar("PUT","/eu/nutri/preferencias-agenda",a.token(),Map.of("antecedencia","DUAS_HORAS","lembreteExtraUmaHora",true));
		var inicio=em(30*60);var consulta=agendar(a,p,inicio,Map.of());
		// relógio controlado: 3 h antes ainda não; 2 h antes avisa os dois; 1 h antes, o extra
		lembretes.executar(inicio.minus(3,ChronoUnit.HOURS));
		assertThat(avisos(p,consulta)).isZero();
		lembretes.executar(inicio.minus(2,ChronoUnit.HOURS));lembretes.executar(inicio.minus(110,ChronoUnit.MINUTES));
		assertThat(avisos(p,consulta)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from aviso where usuario_id=? and chave_dedup like ?",Integer.class,a.usuario(),"consulta:"+consulta+":%")).isEqualTo(1);
		lembretes.executar(inicio.minus(1,ChronoUnit.HOURS));lembretes.executar(inicio.minus(30,ChronoUnit.MINUTES));
		assertThat(avisos(p,consulta)).isEqualTo(2);
		var corpo=jdbc.queryForObject("select corpo from aviso where usuario_id=?::uuid and chave_dedup=?",String.class,p.usuarioId(),"consulta:"+consulta+":"+inicio.getEpochSecond()+":1h");
		assertThat(corpo).doesNotContainPattern("\\d{1,2}:\\d{2}");
	}

	@Test void padraoEhUmDiaEOsDoisDiasAvisamAntes() throws Exception {
		var a=cadastrarNutri();var p=pacienteVinculado(a);var b=cadastrarNutri();var q=pacienteVinculado(b);
		requisitar("PUT","/eu/nutri/preferencias-agenda",b.token(),Map.of("antecedencia","DOIS_DIAS","lembreteExtraUmaHora",false));
		var inicio=em(10*24*60);var deA=agendar(a,p,inicio,Map.of());var deB=agendar(b,q,inicio,Map.of());
		lembretes.executar(inicio.minus(36,ChronoUnit.HOURS));
		assertThat(avisos(p,deA)).isZero();assertThat(avisos(q,deB)).isEqualTo(1);
		lembretes.executar(inicio.minus(23,ChronoUnit.HOURS));
		assertThat(avisos(p,deA)).isEqualTo(1);
		// sem lembrete extra, a última hora não gera outro
		lembretes.executar(inicio.minus(30,ChronoUnit.MINUTES));
		assertThat(avisos(p,deA)).isEqualTo(1);assertThat(avisos(q,deB)).isEqualTo(1);
	}

	@Test void agendarEnviaConfirmacaoComLinkSoNaOnlineERespeitaQuemDesligou() throws Exception {
		jdbc.update("update notificacao_pendente set status='EXPIRADA',payload_cifrado=null where tipo='CONSULTA_AGENDADA'");
		var a=cadastrarNutri();var p=pacienteVinculado(a);var q=pacienteVinculado(a);
		var inicio=em(3*24*60);
		agendar(a,p,inicio,Map.of("modalidade","ONLINE","linkOnline","https://meet.example.invalid/sala-1"));
		var lista=confirmacoes();
		assertThat(lista).hasSize(1);
		var email=lista.get(0);
		assertThat(email.inicio()).isEqualTo(inicio);assertThat(email.link()).isEqualTo("https://meet.example.invalid/sala-1");assertThat(email.expiraEm()).isEqualTo(inicio);
		assertThat(email.destinatario()).isEqualTo(jdbc.queryForObject("select email from usuario where id=?::uuid",String.class,p.usuarioId()));
		// presencial: sem link; quem desligou o lembrete de consulta não recebe (RN-NOT-03)
		agendar(a,p,inicio.plus(1,ChronoUnit.DAYS),Map.of());
		assertThat(confirmacoes()).hasSize(2).anyMatch(e->e.link()==null);
		jdbc.update("insert into preferencia_notificacao(usuario_id,silencio_ativo,tipos_desligados) values(?::uuid,false,'[\"CONSULTA_AMANHA\"]'::jsonb) on conflict(usuario_id) do update set tipos_desligados=excluded.tipos_desligados",q.usuarioId());
		agendar(a,q,inicio,Map.of());
		assertThat(confirmacoes()).hasSize(2);
	}
}
