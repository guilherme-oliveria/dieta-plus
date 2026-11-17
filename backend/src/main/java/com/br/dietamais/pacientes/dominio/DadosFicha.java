package com.br.dietamais.pacientes.dominio;

import com.br.dietamais.pacientes.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public record DadosFicha(String nome,String email,LocalDate dataNascimento,Sexo sexo,String telefone,
		BigDecimal estaturaReferencia,String objetivo,String observacoes,CadastroAmpliado cadastro,List<RestricaoSaude> restricoes) {
	public DadosFicha {
		nome=nome.strip();email=limpar(email);if(email!=null)email=email.toLowerCase(Locale.ROOT);
		telefone=limpar(telefone);objetivo=limpar(objetivo);observacoes=limpar(observacoes);
	}
	public static DadosFicha de(CriarPacienteRequisicao r){return new DadosFicha(r.nome(),r.email(),r.dataNascimento(),r.sexo(),r.telefone(),r.estaturaReferencia(),r.objetivo(),r.observacoes(),r.cadastroAmpliado(),r.restricoesSaude());}
	public static DadosFicha de(AtualizarPacienteRequisicao r){return new DadosFicha(r.nome(),r.email(),r.dataNascimento(),r.sexo(),r.telefone(),r.estaturaReferencia(),r.objetivo(),r.observacoes(),r.cadastroAmpliado(),r.restricoesSaude());}
	/** Mesmos dados com a lista de itens de saúde já normalizada (ids e textos). */
	public DadosFicha comRestricoes(List<RestricaoSaude> normalizadas){return new DadosFicha(nome,email,dataNascimento,sexo,telefone,estaturaReferencia,objetivo,observacoes,cadastro,normalizadas);}
	private static String limpar(String valor){return valor==null||valor.isBlank()?null:valor.strip();}
}
