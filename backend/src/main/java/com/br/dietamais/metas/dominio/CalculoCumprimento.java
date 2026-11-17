package com.br.dietamais.metas.dominio;

import com.br.dietamais.metas.api.FrequenciaMeta;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Map;

/**
 * RN-MET-03: cumprimento de uma meta numa janela. Função pura, com os mesmos vetores do app (`contrato/calculos/metas.json`).
 * A janela é inclusiva, cortada pelo início e pelo fim da meta e por {@code hoje} (dia futuro não é previsto). Registros do mesmo dia somam;
 * o dia cumpre com soma maior ou igual ao alvo. SEMANAL: semanas de segunda a domingo, previstos = N por semana (limitado aos dias da semana dentro da janela).
 */
public final class CalculoCumprimento {
	private CalculoCumprimento(){}

	public record Resultado(int diasPrevistos,int diasCumpridos,BigDecimal percentual,int sequenciaAtual,int melhorSequencia) {}

	public static Resultado calcular(FrequenciaMeta frequencia,BigDecimal alvo,Integer vezesPorSemana,LocalDate inicioMeta,LocalDate fimMeta,
			LocalDate de,LocalDate ate,LocalDate hoje,Map<LocalDate,BigDecimal> somaPorDia){
		var primeiro=inicioMeta!=null&&inicioMeta.isAfter(de)?inicioMeta:de;
		var ultimo=ate;
		if(fimMeta!=null&&fimMeta.isBefore(ultimo))ultimo=fimMeta;
		if(hoje.isBefore(ultimo))ultimo=hoje;
		if(ultimo.isBefore(primeiro))return new Resultado(0,0,BigDecimal.ZERO.setScale(1),0,0);
		// cada unidade: dias previstos, dias cumpridos (já limitados ao previsto), cumprida?, aberta?
		var unidades=new ArrayList<int[]>();
		if(frequencia==FrequenciaMeta.DIARIA){
			for(var d=primeiro;!d.isAfter(ultimo);d=d.plusDays(1)){
				boolean cumpre=cumpre(somaPorDia,d,alvo);
				unidades.add(new int[]{1,cumpre?1:0,cumpre?1:0,d.isBefore(hoje)?0:1});
			}
		}else{
			int n=vezesPorSemana==null?1:vezesPorSemana;
			for(var segunda=primeiro.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));!segunda.isAfter(ultimo);segunda=segunda.plusWeeks(1)){
				var inicio=segunda.isBefore(primeiro)?primeiro:segunda;
				var domingo=segunda.plusDays(6);var fim=domingo.isAfter(ultimo)?ultimo:domingo;
				int dias=(int)ChronoUnit.DAYS.between(inicio,fim)+1;
				int previstos=Math.min(n,dias);int cumpridos=0;
				for(var d=inicio;!d.isAfter(fim);d=d.plusDays(1))if(cumpre(somaPorDia,d,alvo))cumpridos++;
				int validos=Math.min(cumpridos,previstos);
				unidades.add(new int[]{previstos,validos,validos>=previstos?1:0,domingo.isBefore(hoje)?0:1});
			}
		}
		int previstos=0,cumpridos=0,melhor=0,corrida=0;
		for(var u:unidades){
			previstos+=u[0];cumpridos+=u[1];
			if(u[2]==1){corrida++;melhor=Math.max(melhor,corrida);}else corrida=0;
		}
		// sequência atual: unidade ainda aberta e não cumprida não quebra a sequência
		int atual=0;int i=unidades.size()-1;
		if(i>=0&&unidades.get(i)[2]==0&&unidades.get(i)[3]==1)i--;
		for(;i>=0&&unidades.get(i)[2]==1;i--)atual++;
		var percentual=previstos==0?BigDecimal.ZERO.setScale(1):BigDecimal.valueOf(cumpridos*100L).divide(BigDecimal.valueOf(previstos),1,RoundingMode.HALF_UP);
		return new Resultado(previstos,cumpridos,percentual,atual,melhor);
	}
	private static boolean cumpre(Map<LocalDate,BigDecimal> soma,LocalDate dia,BigDecimal alvo){
		var v=soma.get(dia);return v!=null&&v.compareTo(alvo)>=0;
	}
}
