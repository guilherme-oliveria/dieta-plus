// MOB-97: ponta a ponta das histórias da 1.4.0 (H-01 a H-07) na web, contra o backend real e a Maria dos dados de exemplo.
//
// O que é preparado pela API (a web é só online, então é o mesmo caminho que a tela usa) e o que é conferido na tela:
//  - H-01 restrição: a nutri cadastra a alergia na aba Saúde pela tela; a ficha mostra o item depois de recarregar.
//  - H-02 consulta: agendada online com link, marcada como realizada (RN-AGE-03); a paciente vê a consulta em /eu/consultas.
//  - H-03 fora do plano: a paciente registra pela tela do Hoje ("Comi algo fora do plano").
//  - H-04 e H-05 meta, suplemento e orientação: criados pela API; a nutri vê nas abas Metas e Prescrições.
//  - H-06 documento: declaração de comparecimento da consulta realizada; a conferência pública diz "VALIDO";
//    a nutri vê o código na aba Documentos e a paciente vê o documento no Perfil.
//  - H-07 planos: a comparação aparece na Conta da nutri.
// Cada tela nova passa pela trava de acessibilidade (axe-core, conferirAcessibilidade).
//
// Fora daqui: o alerta do montador ao pôr um alimento marcado na dieta (precisa montar a dieta inteira pela tela; a regra
// tem teste de API no backend) e o humor depois do "Comi" (o diálogo já abre no fluxo-completo.spec.ts).
import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import {
  botao, buscarPaciente, campo, clicar, conferirAcessibilidade, digitar, entrar, NUTRI_EXEMPLO, SENHA_EXEMPLO, tocar, tocarAba, veTexto,
} from "./apoio.ts";

const sufixo = Date.now().toString(36);
const MARIA = { nome: "Maria Exemplo", email: "maria@exemplo.dev" };
const suplemento = `Vitamina D ${sufixo}`;
const orientacao = `Hidratação ${sufixo}`;
const linkConsulta = `https://meet.example.com/e2e-${sufixo}`;
let api: APIRequestContext;
let tokenNutri = "";
let mariaId = "";
let codigoDocumento = "";

/** Data de hoje no fuso da nutri e da paciente (São Paulo), em ISO (aaaa-mm-dd). */
const hoje = () => new Date().toLocaleDateString("sv-SE", { timeZone: "America/Sao_Paulo" });
const dispositivo = () => ({ id: crypto.randomUUID(), plataforma: "DESKTOP", nomeAparelho: "e2e" });
const autorizacao = (token: string) => ({ Authorization: `Bearer ${token}` });
const comChave = (token: string) => ({ ...autorizacao(token), "Idempotency-Key": crypto.randomUUID() });

async function token(email: string) {
  const r = await api.post("/api/v1/auth/login", { headers: { "Idempotency-Key": crypto.randomUUID() }, data: { email, senha: SENHA_EXEMPLO, dispositivo: dispositivo() } });
  expect(r.status(), await r.text()).toBe(200);
  return (await r.json()).accessToken as string;
}

async function criar(caminho: string, dados: unknown) {
  const r = await api.post(caminho, { headers: comChave(tokenNutri), data: dados });
  expect(r.status(), `${caminho}: ${await r.text()}`).toBeLessThan(300);
  return r;
}

/** Página nova já logada como a nutri, com a ficha da Maria aberta. */
async function fichaDaMaria(page: Page) {
  await entrar(page, NUTRI_EXEMPLO);
  await expect(page.getByRole("heading", { name: "Início" })).toBeAttached({ timeout: 30_000 });
  await tocar(page, "Pacientes");
  const titulo = page.getByRole("heading", { name: MARIA.nome });
  await expect(async () => {
    if ((await titulo.count()) === 0) {
      await buscarPaciente(page, "Maria");
      await clicar(page, page.getByRole("button", { name: new RegExp(MARIA.nome) }));
    }
    await expect(titulo).toBeAttached({ timeout: 5_000 });
  }).toPass({ timeout: 40_000 });
}

test.describe.configure({ mode: "serial" });

test.beforeAll(async ({ playwright }) => {
  api = await playwright.request.newContext({ baseURL: "http://localhost:8081" });
  tokenNutri = await token(NUTRI_EXEMPLO);
  const lista = await api.get("/api/v1/pacientes?pagina=0&tamanho=100", { headers: autorizacao(tokenNutri) });
  expect(lista.status()).toBe(200);
  const dados = await lista.json();
  const itens: Array<{ id: string; nome: string }> = dados.itens ?? dados.conteudo ?? dados;
  mariaId = itens.find((p) => p.nome === MARIA.nome)?.id ?? "";
  expect(mariaId, "a Maria dos dados de exemplo precisa existir").not.toBe("");
});

test.afterAll(async () => { await api?.dispose(); });

test("H-01: a nutri cadastra a alergia na aba Saúde", async ({ page, browser }) => {
  await fichaDaMaria(page);
  await tocarAba(page, "Saúde");
  await veTexto(page, "Saúde e restrições");
  await conferirAcessibilidade(page, "Ficha: Saúde");
  await tocar(page, "Adicionar");
  await veTexto(page, "Novo item de saúde");
  // Alergia é o tipo padrão; o nome sugere o marcador "Amendoim" para o alerta do montador.
  await digitar(page, "Nome", "Amendoim");
  await conferirAcessibilidade(page, "Ficha: novo item de saúde");
  const salvou = page.waitForResponse((r) => r.url().includes(`/pacientes/${mariaId}`) && r.request().method() !== "GET", { timeout: 30_000 });
  await tocar(page, "Salvar");
  expect((await salvou).status()).toBeLessThan(300);

  // A restrição volta do servidor. A ficha abre numa página nova: a árvore do DOM fica presa no formulário fechado
  // (ver fluxo-completo), e na mesma página o F5 manteria a sessão e pularia a tela de entrar.
  const ficha = await api.get(`/api/v1/pacientes/${mariaId}`, { headers: autorizacao(tokenNutri) });
  expect(JSON.stringify((await ficha.json()).restricoesSaude ?? [])).toContain("AMENDOIM");
  const ctx = await browser.newContext({ locale: "pt-BR", timezoneId: "America/Sao_Paulo", viewport: { width: 1280, height: 900 } });
  const nova = await ctx.newPage();
  await fichaDaMaria(nova);
  await tocarAba(nova, "Saúde");
  await veTexto(nova, "Amendoim");
  await veTexto(nova, /Marca: Amendoim/);
  await ctx.close();
});

test("H-04 e H-05: meta, suplemento e orientação aparecem na ficha", async ({ page }) => {
  await criar(`/api/v1/pacientes/${mariaId}/metas`, { tipo: "ATIVIDADE_MIN", alvo: 30, frequencia: "DIARIA", inicio: hoje() });
  await criar(`/api/v1/pacientes/${mariaId}/suplementos`, { nome: suplemento, dose: "2000 UI", forma: "cápsula", frequencia: "1 vez ao dia", horarios: ["08:00"], inicio: hoje() });
  await criar(`/api/v1/pacientes/${mariaId}/orientacoes`, { titulo: orientacao, texto: "Beba **água** ao longo do dia:\n\n- 1 copo ao acordar" });

  await fichaDaMaria(page);
  await tocarAba(page, "Metas");
  await expect(page.getByRole("button", { name: "Nova meta" }).first()).toBeAttached({ timeout: 20_000 });
  await veTexto(page, "Cumprimento na semana");
  await conferirAcessibilidade(page, "Ficha: Metas");

  await tocarAba(page, "Prescrições");
  await veTexto(page, suplemento);
  await conferirAcessibilidade(page, "Ficha: Prescrições");
  // Suplementos e Orientações são seções da aba, trocadas por chip (FilterChip: no DOM pode sair como checkbox).
  await clicar(page, page.getByRole("checkbox", { name: "Orientações", exact: true }).or(botao(page, "Orientações")).first());
  await veTexto(page, orientacao);
  await conferirAcessibilidade(page, "Ficha: Prescrições, Orientações");
});

test("H-02 e H-06: consulta online realizada vira declaração conferível", async ({ page, browser }) => {
  const inicio = new Date(Date.now() + 60 * 60 * 1000).toISOString();
  const r = await criar("/api/v1/consultas", {
    pacienteId: mariaId, inicio, duracaoMin: 50, tipo: "AVALIACAO", modalidade: "ONLINE", linkOnline: linkConsulta,
    orientacaoPaciente: "Teste de ponta a ponta.",
  });
  const consulta = await r.json();
  expect(consulta.linkOnline).toBe(linkConsulta);
  expect(consulta.status).toBe("AGENDADA");

  // RN-AGE-03: AGENDADA vai para REALIZADA; repetir o mesmo status é recusado.
  const etag = r.headers()["etag"] ?? `"${consulta.versao}"`;
  const realizada = await api.patch(`/api/v1/consultas/${consulta.id}`, { headers: { ...autorizacao(tokenNutri), "If-Match": etag }, data: { status: "REALIZADA" } });
  expect(realizada.status(), await realizada.text()).toBe(200);
  const etag2 = realizada.headers()["etag"] ?? `"${(await realizada.json()).versao}"`;
  const repetida = await api.patch(`/api/v1/consultas/${consulta.id}`, { headers: { ...autorizacao(tokenNutri), "If-Match": etag2 }, data: { status: "REALIZADA" } });
  expect(repetida.status()).toBe(400);

  // A paciente vê a consulta dela (sem a observação interna da nutri).
  const tokenMaria = await token(MARIA.email);
  const daPaciente = await api.get("/api/v1/eu/consultas", { headers: autorizacao(tokenMaria) });
  expect(daPaciente.status()).toBe(200);
  const textoPaciente = await daPaciente.text();
  expect(textoPaciente).toContain(consulta.id);
  expect(textoPaciente).not.toContain("observacaoInterna\":\"");

  // Declaração de comparecimento pelo modelo do sistema, presa à consulta realizada.
  const modelos = await api.get("/api/v1/eu/nutri/modelos-documento", { headers: autorizacao(tokenNutri) });
  const lista: Array<{ id: string; tipo: string; doSistema: boolean }> = (await modelos.json()).itens;
  const modelo = lista.find((m) => m.doSistema && m.tipo === "DECLARACAO_COMPARECIMENTO");
  expect(modelo, "o modelo do sistema de declaração precisa existir").toBeTruthy();
  const emitido = await (await criar(`/api/v1/pacientes/${mariaId}/documentos`, { modeloId: modelo!.id, consultaId: consulta.id })).json();
  codigoDocumento = emitido.codigoConferencia;
  expect(codigoDocumento).toMatch(/^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/);

  // Conferência pública: sem login.
  const conferencia = await api.get(`/api/v1/documentos/conferir/${codigoDocumento}`);
  expect(conferencia.status()).toBe(200);
  expect((await conferencia.json()).situacao).toBe("VALIDO");

  // A nutri vê o documento válido, com o código, na aba Documentos.
  await fichaDaMaria(page);
  await tocarAba(page, "Documentos");
  await veTexto(page, new RegExp(codigoDocumento));
  await veTexto(page, "Válido");
  await conferirAcessibilidade(page, "Ficha: Documentos");

  // A paciente vê o documento no Perfil.
  const ctx = await browser.newContext({ locale: "pt-BR", timezoneId: "America/Sao_Paulo", viewport: { width: 1280, height: 900 } });
  const paciente = await ctx.newPage();
  await entrar(paciente, MARIA.email);
  await expect(paciente.getByRole("heading", { name: "Hoje" })).toBeAttached({ timeout: 30_000 });
  await tocar(paciente, "Perfil");
  await veTexto(paciente, "Documentos");
  await veTexto(paciente, "Declaração de comparecimento");
  await conferirAcessibilidade(paciente, "Perfil com documentos");
  await ctx.close();
});

test("H-03: a paciente registra o que comeu fora do plano", async ({ page }) => {
  await entrar(page, MARIA.email);
  await expect(page.getByRole("heading", { name: "Hoje" })).toBeAttached({ timeout: 30_000 });
  await tocar(page, "Comi algo fora do plano");
  // O rótulo do CampoTexto não sai como texto no DOM: é o nome acessível do próprio campo.
  await expect(page.getByRole("heading", { name: "Comi algo fora do plano" }).first()).toBeAttached({ timeout: 20_000 });
  await expect(campo(page, "O que você comeu?").first()).toBeAttached({ timeout: 20_000 });
  await digitar(page, "O que você comeu?", `Pipoca ${sufixo}`);
  await conferirAcessibilidade(page, "Comi algo fora do plano");
  const registrou = page.waitForResponse((r) => r.url().includes("/registros-fora-do-plano") && r.request().method() === "POST", { timeout: 30_000 });
  await tocar(page, "Registrar");
  expect((await registrou).status()).toBeLessThan(300);
});

test("H-07: a nutri compara os planos na Conta", async ({ page }) => {
  await entrar(page, NUTRI_EXEMPLO);
  await expect(page.getByRole("heading", { name: "Início" })).toBeAttached({ timeout: 30_000 });
  await tocar(page, "Conta");
  await expect(page.getByRole("heading", { name: "Minha assinatura" })).toBeAttached({ timeout: 30_000 });
  // Na web os planos vêm como cartões de escolha (radio) com preço e limite; o título "Planos" só existe na versão sem preço
  // das lojas (D-09). O texto do cartão pode sair no nome acessível do radio, por isso as duas formas.
  const limite = /Até \d+ pacientes|Pacientes sem limite/;
  await expect(page.getByRole("radio", { name: limite }).or(page.getByText(limite)).first()).toBeAttached({ timeout: 30_000 });
  await conferirAcessibilidade(page, "Conta: planos");
});
