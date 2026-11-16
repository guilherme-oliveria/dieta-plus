# Análise de lacunas: Dieta+ × A Nutri

> 16/11/2026. Base: captura do A Nutri (app da nutri, app do paciente e landing, 13 a 16/11/2026) e `develop` do Dieta+ no mesmo dia. Ver [`README.md`](README.md) para as regras de uso.
> Legenda da coluna **Situação**: **Lacuna** (o A Nutri tem, o Dieta+ não) · **Pior** (os dois têm, o do Dieta+ é mais fraco) · **Paridade** · **Melhor** (o Dieta+ já faz melhor).

## 0. Resumo para o PO

1. **No núcleo clínico o Dieta+ já está à frente.** Avaliação com paridade de planilha (7 dobras, protocolos por nutri, infantil com OMS e Frisancho), 395 exames classificados automaticamente, dieta por porções contra metas com adequação à DRI, modelos por condição e PDFs da lista em 4 tamanhos. O A Nutri não tem nada equivalente a isso com o mesmo rigor; ele compensa com IA.
2. **As maiores lacunas estão em volta do núcleo**, no que faz a nutri economizar tempo e o paciente voltar ao app: segurança alimentar na ficha (alergias e intolerâncias que alertam no montador), diário do paciente mais rico (humor, "comi fora do plano"), metas de hábito com progresso, prescrição de suplementos e orientações avulsas, documentos (atestado, declaração) e uma agenda com status e modalidade de verdade.
3. **Comercial:** o Dieta+ cobra menos (R$ 29,90) mas trava em 20 pacientes, não tem plano anual nem um segundo nível de plano. O concorrente vende capacidade (80 pacientes no plano de entrada, ilimitado no seguinte) e dá 2 meses no anual. Há espaço para o Dieta+ manter o preço baixo e ganhar com um plano intermediário e o anual.
4. **Não vale correr atrás de tudo.** Assistente de IA, teleconsulta, site da clínica com diretório, equipe com recepção, drive e comunidade são produtos inteiros; ficam fora da 1.4.0 (seção 6).
5. **Landing page:** recomendo uma pasta `site/` neste monorepo, estática, com os preços lidos da API (seção 5). Bloqueio real antes de publicar qualquer landing: os termos de uso e a política de privacidade, que ainda estão em rascunho (BE-36).

## 1. Os dois produtos lado a lado

| | A Nutri | Dieta+ |
| --- | --- | --- |
| Público | Nutricionista autônoma, pequena clínica (até 2 nutris + recepção) e estudante | Nutricionista autônoma; clínica modelada (`organizacao`) mas não exposta |
| Plataformas | Web (SPA) para a nutri; PWA e Android para o paciente | Android, iOS, Desktop e web (`wasmJs`), nutri e paciente no mesmo app |
| Offline | Não | Sim (paciente lê e registra offline; nutri edita no desktop/tablet com resolução de conflito) |
| Posicionamento | "Faça mais rápido com IA": a IA prepara, a nutri revisa | Substituir a planilha e o PDF com cálculo fiel e app do paciente |
| Preço de entrada | R$ 79,90/mês, 80 pacientes | R$ 29,90/mês, 20 pacientes |
| Teste grátis | 14 dias, sem cartão | 30 dias, 5 pacientes `[SUPOSIÇÃO S1]` |
| Cobrança | Stripe, mensal ou anual | Asaas (PIX, cartão, boleto), mensal, checkout fora do app |

## 2. Comparação por área

### 2.1 Conta, cadastro e acesso

| Área | A Nutri | Dieta+ hoje | Situação | ID |
| --- | --- | --- | --- | --- |
| Cadastro da nutri | Nome, CPF, e-mail, telefone, CRN com regional + número + sufixo, termos, opt-in de WhatsApp; conta de estudante | Nome, e-mail, senha, CRN e termos; confirmação por e-mail | Paridade (CRN sem validação de regional) | L-01 |
| Verificação profissional | Envio de diploma/carteira do CRN em até 30 dias, selo de verificado | Não existe | Lacuna (baixa prioridade enquanto não houver vitrine) | L-02 |
| Acesso do paciente | Código de 6 caracteres, 30 dias; nutri escolhe os módulos visíveis por paciente | Convite de 8 caracteres + link, 7 dias, com consentimento LGPD no aceite | Paridade; falta escolher módulos por paciente | L-03 |
| Importar dados de outro sistema | Importação e "desfazer importação"; ferramenta para migrar de concorrente | Não existe (importação da planilha está fora do MVP) | Lacuna (alto valor para aquisição) | L-04 |

### 2.2 Prontuário

| Área | A Nutri | Dieta+ hoje | Situação | ID |
| --- | --- | --- | --- | --- |
| Cadastro do paciente | Dados pessoais, CPF, contato, endereço com CEP, objetivo, meta de peso, responsável, tags, status Ativo/Inativo/Alta | Ficha + cadastro ampliado (BE-64/MOB-78): escolaridade, estado civil, profissão, endereço, convênio, indicação, motivo; vínculo Convidado/Ativo/Encerrado | Pior: sem tags, sem meta de peso, sem responsável, sem "alta" | L-05 |
| Lista de pacientes | Busca por nome/CPF, filtro por status e profissional | Busca, filtros de atenção, ordenação, próxima consulta e última avaliação, preparada para 1000 pacientes (MOB-67, MOB-UX-01 a 03) | Melhor | — |
| Saúde e restrições | Alergias, intolerâncias, condições (CID), medicamentos; **alergia bloqueia e intolerância alerta no plano** | Marcadores no alimento (lactose, glúten, sódio, açúcar, potássio) e restrições vindas do modelo de dieta; nada na ficha | **Lacuna (segurança)** | L-06 |
| Anamnese | Construtor com campos calculados; 15 modelos sugeridos; formulário pré-consulta por link | Modelo editável, envio ao app, resposta em passos, calculadoras (PA, álcool, sal, óleo), leitura por temas | Paridade (falta link sem conta) | L-07 |
| Antropometria | Cálculo ao vivo, Pollock 3, RCQ, CMB, bioimpedância | Paridade de planilha: 7 dobras, protocolos por nutri, prévia ao vivo (BE-55c), 4 gráficos, PDF | Melhor | — |
| Gasto energético | 20+ fórmulas, inclusive criança, gestante e lactante | GEB/GET FAO, IOM, RDI, MET; peso ajustado; gestação e lactação | Paridade | — |
| Crianças e adolescentes | Fórmulas infantis no cálculo energético | Curvas OMS, Frisancho, percentis e escore Z, avaliação infantil completa | Melhor | — |
| Exames | Lançamento manual com faixa ideal; leitura de PDF por IA; "N alterados" | Catálogo de 395 exames, classificação automática por faixa etária e sexo, régua de faixas, compartilhamento por seção | Melhor no cálculo; **lacuna no anexo do PDF do laboratório** | L-08 |
| Evolução clínica | Nota ADIME com diagnóstico PES, rascunho e "assinar e finalizar" | Parecer na avaliação; consulta sem nota própria | Lacuna | L-09 |
| Fotos de evolução | Fotos corporais, pedido de foto ao paciente | Não existe ("foto do prato" está fora do MVP) | Lacuna (decisão do Guilherme) | L-10 |
| Arquivos do paciente | Anexos e drive por paciente | Não existe | Lacuna | L-08 |

### 2.3 Plano alimentar e catálogos

| Área | A Nutri | Dieta+ hoje | Situação | ID |
| --- | --- | --- | --- | --- |
| Montador | Quantitativo, qualitativo e por equivalência; totais e análise de nutrientes | Por alimento e por porções do grupo, metas com saldo, alternativas, itens à vontade e texto livre, micronutrientes e adequação DRI | Melhor | — |
| Substituição | Lista por equivalência; no quantitativo, "substituições inteligentes" por similaridade com % | Lista de substituição por paciente, troca na quantidade equivalente, lista modelável e cópia entre pacientes, PDF em 4 tamanhos | Paridade (falta sugestão por similaridade no plano por alimento) | L-11 |
| Alerta de restrição ao montar | Alerta "Intolerância: Lactose" ao adicionar o alimento | Só filtra a lista na publicação quando a restrição veio do modelo | **Pior** | L-06 |
| Catálogo de alimentos | 23,5 mil itens (TACO, TBCA, IBGE, Open Food Facts), importar de rótulo, alimento próprio | TACO (597), composição de 571 alimentos, 400 produtos, alimento e produto próprios | Pior em volume; melhor em rastreio de fonte | L-12 |
| Receitas | Catálogo de receitas com ingredientes, compartilháveis com o paciente | Não existe (fora do MVP) | Lacuna | L-13 |
| Modelos de plano | Categorias e tags, rascunho/publicar | 9 modelos do sistema por condição, modelos da nutri, aplicar escalando pelo VET | Melhor | — |
| Suplementos | Catálogo e prescrição ao paciente (dose, frequência, forma), aparece no app | Produtos como item da dieta; sem prescrição avulsa | **Lacuna** | L-14 |
| Orientações | Orientações avulsas com editor rico e modelos; aviso ao paciente | Rodapé da dieta e texto livre na refeição | Pior | L-14 |
| Documentos | Atestado, receita, declaração por modelo, com assinatura digitalizada e QR Code de validação pública | PDF da dieta, da avaliação e da lista; nenhum documento avulso | **Lacuna** | L-15 |
| Impressão com a marca | Logo e cores da clínica nos PDFs | Logo no PDF (envio da logo pendente: MOB-33b) | Pior | L-15 |

### 2.4 Agenda e atendimento

| Área | A Nutri | Dieta+ hoje | Situação | ID |
| --- | --- | --- | --- | --- |
| Agenda | Dia/semana/mês, filtro por profissional, clique no horário vazio cria consulta | Agenda do dia e da semana | Pior | L-16 |
| Dados da consulta | Tipo (consulta, retorno, avaliação), **modalidade** (presencial/online), duração, orientação para o paciente, observação interna; status Agendada/Realizada/Cancelada/Falta | Tipo `PRIMEIRA`/`RETORNO`/`ONLINE` (mistura tipo e modalidade), lugar, duração; status `AGENDADA`/`CANCELADA`; comparecimento só no financeiro privado | **Pior** | L-16 |
| Lembretes de consulta | E-mail de confirmação, 1º lembrete configurável (1 h a 2 dias), 2º lembrete 1 h antes, cancelamento | Push + e-mail 24 h antes (RN-AGE-02) | Pior (sem confirmação nem horário configurável) | L-16 |
| Agendamento pelo paciente | Horários de atendimento, regras (antecedência, intervalo), pedido ou horário livre, solicitações para aceitar | Não existe | **Lacuna** | L-17 |
| Teleconsulta | Sala de vídeo própria, gravação e transcrição por IA | Não existe | Lacuna (fora da 1.4.0; um campo de link resolve 80%) | L-16 |
| Financeiro | Lançamentos da clínica e por paciente | Financeiro por consulta (comparecimento, valor, forma, pago) e resumo (BE-65/MOB-79) | Paridade | — |
| Relatórios | Visão geral, pacientes, consultas, financeiro, nutrição, por período, com gráficos | Painel do dia; resumo financeiro | **Lacuna** | L-18 |

### 2.5 App do paciente

| Área | A Nutri | Dieta+ hoje | Situação | ID |
| --- | --- | --- | --- | --- |
| Plano do dia | Refeições, kcal, macros, progresso, adesão da semana | Hoje com próxima refeição, seletor de dia, macros, desfazer registro | Paridade | — |
| Registro da refeição | Marcar consumido, escolher a troca, **humor antes/depois**, "comeu algo fora do plano?", foto | Comi/Troquei/Pulei até 3 dias atrás, offline, desfazer | Pior (sem humor e sem extra) | L-19 |
| Metas | 10 tipos (água, caminhada, sono, frutas e verduras, contador, medida...), fases, níveis, pontos, sequência, conquistas | Só água (meta 35 ml/kg) e peso em casa | **Lacuna** | L-20 |
| Medidas e exames | Medidas da última avaliação, gráficos, exames alterados | Meus resultados, evolução, meus exames | Paridade | — |
| Suplementos, orientações, documentos, receitas | Seções no menu "Mais", liberadas pela nutri | Não existem | Lacuna | L-14, L-15 |
| Lista de compras | Por seção do mercado, progresso | Por grupo, N dias, compartilhar, lembra marcados | Paridade | — |
| Consultas | Próximas, histórico, agendar | Próxima consulta e `.ics` | Pior (sem histórico nem agendar) | L-17 |
| Lembretes | Push de refeição e suplemento (precisa de internet) | Lembretes **locais** com horário de silêncio, funcionam offline | Melhor | — |
| Privacidade | Consentimentos revogáveis, exportar, excluir | Consentimento no aceite, exportar, excluir | Paridade | — |

### 2.6 Plataforma e negócio

| Área | A Nutri | Dieta+ hoje | Situação | ID |
| --- | --- | --- | --- | --- |
| Assistente de IA | 12 a 20 agentes, bases de conhecimento, cota de tokens e pacotes avulsos | Não existe | Lacuna (fora da 1.4.0) | L-21 |
| Equipe e recepção | Papéis por clínica, convite por e-mail, vagas por plano | Organização de 1 nutri (clínica é P2) | Lacuna (P2) | L-22 |
| Site da clínica e diretório | Construtor de 8 seções, SEO, leads, diretório público | Não existe (vitrine de nutris é P2) | Lacuna (P2) | L-22 |
| Drive, estudos, comunidade | Existem | Não existem | Fora do foco | — |
| Ajuda e suporte | FAQ, vídeos, documentação, chat | Não existe dentro do app | Lacuna (baixo esforço com FAQ estático) | L-23 |
| Plano anual e níveis | 3 níveis, mensal e anual (10×) | 1 nível pago, só mensal | **Lacuna** | L-24 |
| Tela de assinatura | Uso de cada recurso, comparar planos, contador do teste ("dia X de 14") | Plano, status, limite e uso (14/20) | Pior (sem comparar planos nem contagem do teste) | L-24 |
| Landing page | Landing configurável, preços da API, SEO, demonstração agendada, blog | Não existe | Lacuna | L-25 |

## 3. O que o Dieta+ já faz melhor (manter e usar na venda)

1. **Funciona sem internet**, para o paciente e para a nutri no consultório, com resolução de conflito. O concorrente é só online.
2. **App nativo em Android, iOS e Desktop**, além da web. O concorrente tem iOS "em breve".
3. **Cálculo fiel à planilha clínica**, com vetores de teste: 7 dobras, protocolos por nutri, prévia ao vivo, peso ajustado, metas e adequação à DRI.
4. **Crianças e adolescentes** com curvas da OMS, Frisancho, percentil e escore Z.
5. **Exames classificados automaticamente** (395 exames, por idade e sexo) com régua de faixas para o paciente.
6. **Dieta por porções contra as metas**, alternativas, itens à vontade e modelos por condição que já tiram os alimentos marcados.
7. **PDFs da lista de substituição em 4 tamanhos** e PDF da avaliação com gráficos.
8. **Lembretes locais e agenda `.ics`**: o paciente recebe mesmo sem internet e vê as refeições no calendário do celular.
9. **Preço de entrada menor** (R$ 29,90 contra R$ 79,90).

Esses pontos devem aparecer na landing e nas notas da 1.4.0, com as palavras do Dieta+.

## 4. Parte comercial

### 4.1 Planos lado a lado

| | A Nutri: entrada | A Nutri: intermediário | A Nutri: equipe | Dieta+ Teste | Dieta+ Básico |
| --- | --- | --- | --- | --- | --- |
| Mensal | R$ 79,90 | R$ 179,90 | R$ 279,90 | grátis | R$ 29,90 |
| Anual | R$ 799,00 (10×) | R$ 1.799,00 | R$ 2.799,00 | — | — |
| Pacientes | 80 | ilimitados (até encher o armazenamento) | ilimitados | 5 | 20 |
| Nutris | 1 | 1 | 2 + recepção | 1 | 1 |
| Teste | 14 dias, sem cartão, no plano de entrada | | | 30 dias | |
| Diferencial por nível | Capacidade (pacientes, IA, armazenamento); todos os módulos em todos | | | | |

Outras práticas observadas: selo de "pré-lançamento" nos preços; pacotes avulsos de IA; ao atingir um limite, só aquela função trava e o resto continua acessível (igual ao RN-ASS-08 do Dieta+); inadimplência suspende com aviso prévio e o paciente vê "acesso indisponível" (o Dieta+ é melhor aqui: RN-ASS-05 nunca pune o paciente); exportação completa grátis ao cancelar.

### 4.2 Leitura

- **Custo por paciente:** o Dieta+ sai a R$ 1,50 por paciente no limite (29,90 / 20); o concorrente a R$ 1,00 (79,90 / 80). O preço baixo do Dieta+ esconde um teto baixo: uma nutri com 30 pacientes ativos não tem para onde ir.
- **Teste com 5 pacientes é pouco** para a nutri sentir o produto com a carteira real; o prazo de 30 dias é bom.
- **Todos os módulos em todos os planos** é um bom princípio, e o Dieta+ já faz assim. Diferenciar por capacidade é simples de explicar e já cabe na tabela `plano` (RN-ASS-01).

### 4.3 Recomendações (valores são decisão do Guilherme)

1. **Criar um segundo nível de plano** com mais pacientes (direção: 60 a 100 pacientes), mantendo o Básico barato como porta de entrada. Só dado na tabela `plano` + tela de comparar planos (H-07).
2. **Plano anual com desconto de 2 meses** (anual = 10× o mensal). O Asaas aceita cobrança anual; o checkout continua fora do app (RN-ASS-06).
3. **Teste:** manter 30 dias e subir o limite do Teste para algo como 10 a 15 pacientes, configurável.
4. **Tela de assinatura:** comparar planos (mensal/anual), contagem do teste ("faltam N dias") e uso de cada limite. Avisos de fim de teste por e-mail e no painel.
5. **Não vender pacote avulso** enquanto não houver recurso de consumo (IA, armazenamento) que justifique.

## 5. Landing page: onde morar e o que precisa ter

### 5.1 Recomendação: pasta `site/` neste monorepo

| Opção | A favor | Contra |
| --- | --- | --- |
| **`site/` no monorepo (recomendada)** | Mesmo Gitflow, PR e QA; CI por caminho já existe (`_mudancas.yml` ganha um filtro `site/**`); tag própria `site-vX.Y.Z` como já há `backend-v*` e `mobile-v*`; deploy no mesmo Caddy da VPS; quem muda preço ou funcionalidade atualiza a landing no mesmo fluxo | Mais uma pasta no repositório; o `AGENTS.md` da raiz precisa citar o terceiro projeto |
| Repositório separado | Ciclo próprio, dá para dar acesso a alguém de marketing sem abrir o código do app | Segundo fluxo de CI, de proteção de branch e de QA para manter; risco de a landing prometer o que o app não faz |
| Dentro do `mobile/webApp` (wasmJs) | Nenhum projeto novo | Ruim para SEO e para o tempo de carregamento (o bundle da web tem 5,5 MB gzip); a landing precisa ser HTML leve e indexável |

Forma sugerida: site **estático** (HTML gerado, sem servidor próprio), servido pelo Caddy na raiz do domínio, com o app web em um subcaminho ou subdomínio. Os **preços vêm de um endpoint público** do backend (`GET /api/v1/planos`, só leitura, sem dado pessoal), para que mudar preço continue sem deploy (RN-ASS-01). Criar a landing é uma tarefa futura (`SITE-01`), não desta release.

### 5.2 O que a landing precisa ter (funcional, com texto e visual próprios)

1. Proposta de valor e para quem é (nutri autônoma).
2. Funcionalidades, puxando pelos diferenciais da seção 3 (offline, nativo, cálculo fiel, crianças, exames).
3. App do paciente: links das lojas e da web.
4. Planos e preços lidos da API, com alternância mensal/anual e "teste grátis de N dias".
5. Perguntas frequentes (LGPD, cancelamento, exportação dos dados, o paciente não paga).
6. Contato (e-mail e WhatsApp) e chamada para criar conta.
7. Rodapé com **termos de uso e política de privacidade publicados**.
8. SEO básico: título e descrição, Open Graph, dados estruturados de `SoftwareApplication` com as ofertas **coerentes com a tabela de planos** (o concorrente tem preço divergente no JSON-LD), `sitemap.xml`.
9. Métricas sem cookie de terceiros, ou com banner de consentimento se usar analytics com cookie.

### 5.3 Bloqueios antes de publicar

- Termos de uso (`backend/docs/legal/terms-of-use.md`, em rascunho) e política de privacidade aprovados: o cadastro já está bloqueado por isso (BE-36).
- Domínio e VPS no ar (BE-32b, tarefa do Guilherme).
- Conta real do gateway (Asaas) para o "Assinar" funcionar.

## 6. Fora da 1.4.0 (e por quê)

| Lacuna | Motivo |
| --- | --- |
| L-21 Assistente de IA | Produto inteiro, com custo variável por uso e questões de LGPD (dado de saúde enviado a provedor externo). Merece decisão e plano próprios. |
| L-22 Equipe, recepção, site da clínica, diretório | Já planejado como P2 (`visao-geral.md`: clínicas, vitrine de nutris). |
| L-16 Teleconsulta com vídeo próprio | Custo e complexidade altos; o campo de link da consulta online (H-02) cobre a maior parte. |
| L-10 Fotos de evolução e foto do prato | "Foto do prato" está fora do MVP por decisão; exige armazenamento e cuidado de LGPD. Pede decisão do Guilherme. |
| L-02 Verificação profissional | Só faz sentido com vitrine pública. |
| L-12 Catálogo ampliado (TBCA, rótulos) | Conferir licença da TBCA antes; Open Food Facts é ODbL (exige atribuição e compartilhamento da base derivada). |
| L-13 Receitas | Valor real, mas menor que as lacunas do backlog; candidato à 1.5.0. |
