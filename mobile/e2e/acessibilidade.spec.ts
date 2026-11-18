// A6 (MOB-45): trava de acessibilidade da web. Roda o axe-core na árvore de semantics que o Compose publica no DOM.
//
// O que falha o teste: qualquer violação do axe fora das que vêm do próprio Compose para web (lista e motivo em apoio.ts,
// conferirAcessibilidade), tela sem título ou controle sem nome. As telas novas da 1.4.0 também passam por aqui (MOB-97).
import { expect, test } from "@playwright/test";
import { abrir, clicar, conferirAcessibilidade as conferir, entrar, NUTRI_EXEMPLO, tocar, tocarAba, buscarPaciente } from "./apoio.ts";

test("telas de entrada: Entrar, código do convite e cadastro de nutri", async ({ page }) => {
  await abrir(page);
  await conferir(page, "Entrar");
  await tocar(page, "Tenho um código da minha nutri");
  await conferir(page, "Convite: código");
  await tocar(page, "Voltar para entrar");
  await tocar(page, "Sou nutricionista");
  await conferir(page, "Cadastro de nutri");
});

test("nutri: início, pacientes, agenda, alimentos, conta e ficha", async ({ page }) => {
  test.setTimeout(240_000);
  await entrar(page, NUTRI_EXEMPLO);
  await expect(page.getByRole("heading", { name: "Início" })).toBeAttached({ timeout: 30_000 });
  await conferir(page, "Início");
  for (const aba of ["Pacientes", "Agenda", "Alimentos", "Conta"]) {
    await tocar(page, aba);
    await conferir(page, aba);
  }
  await tocar(page, "Pacientes");
  await buscarPaciente(page, "Maria");
  await clicar(page, page.getByRole("button", { name: /Maria Exemplo/ }));
  for (const aba of ["Resumo", "Saúde", "Metas", "Prescrições", "Avaliações", "Evolução", "Dieta", "Lista", "Anamnese", "Documentos"]) {
    await tocarAba(page, aba);
    await conferir(page, `Ficha: ${aba}`);
  }
});

test("nutri: montador de dieta", async ({ page }) => {
  await entrar(page, NUTRI_EXEMPLO);
  await expect(page.getByRole("heading", { name: "Início" })).toBeAttached({ timeout: 30_000 });
  await tocar(page, "Pacientes");
  await buscarPaciente(page, "Maria");
  await clicar(page, page.getByRole("button", { name: /Maria Exemplo/ }));
  await tocar(page, "Dieta");
  await tocar(page, "Nova dieta");
  await expect(page.getByRole("heading", { name: "Montar dieta" })).toBeAttached({ timeout: 20_000 });
  await tocar(page, "Adicionar refeição");
  await conferir(page, "Montador de dieta");
});

test("paciente: hoje, dieta, compras, evolução e perfil", async ({ page }) => {
  test.setTimeout(240_000);
  await entrar(page, "maria@exemplo.dev");
  await expect(page.getByRole("heading", { name: "Hoje" })).toBeAttached({ timeout: 30_000 });
  await conferir(page, "Hoje");
  for (const aba of ["Dieta", "Compras", "Evolução", "Perfil"]) {
    await tocar(page, aba);
    await conferir(page, aba);
  }
});

