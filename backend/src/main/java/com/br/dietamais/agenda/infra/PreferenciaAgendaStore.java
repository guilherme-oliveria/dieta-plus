package com.br.dietamais.agenda.infra;

import com.br.dietamais.agenda.api.AntecedenciaLembrete;
import com.br.dietamais.agenda.api.PreferenciasAgendaResposta;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** RN-AGE-05: uma linha por nutri; sem linha vale o padrão (UM_DIA, sem lembrete extra). */
@Repository
public class PreferenciaAgendaStore {
	public static final PreferenciasAgendaResposta PADRAO=new PreferenciasAgendaResposta(AntecedenciaLembrete.UM_DIA,false);
	private final JdbcTemplate jdbc;
	public PreferenciaAgendaStore(JdbcTemplate jdbc){this.jdbc=jdbc;}

	public PreferenciasAgendaResposta daNutri(UUID nutri){
		return jdbc.query("select antecedencia,lembrete_extra_uma_hora from preferencia_agenda where nutricionista_id=?",
			(rs,i)->new PreferenciasAgendaResposta(AntecedenciaLembrete.valueOf(rs.getString(1)),rs.getBoolean(2)),nutri).stream().findFirst().orElse(PADRAO);
	}
	public void salvar(UUID nutri,PreferenciasAgendaResposta p,Instant agora){
		jdbc.update("insert into preferencia_agenda(nutricionista_id,antecedencia,lembrete_extra_uma_hora,atualizado_em) values(?,?,?,?) "
			+"on conflict(nutricionista_id) do update set antecedencia=excluded.antecedencia,lembrete_extra_uma_hora=excluded.lembrete_extra_uma_hora,atualizado_em=excluded.atualizado_em",
			nutri,p.antecedencia().name(),p.lembreteExtraUmaHora(),Timestamp.from(agora));
	}
}
