// Apoio do e2e da web (Compose/wasmJs).
//
// O app é desenhado num <canvas>, mas a árvore de semantics do Compose chega ao DOM como elementos posicionados sobre o canvas
// (dentro de um shadow root aberto, que o Playwright atravessa): `role`, `aria-label` e texto. Por isso os seletores são
// getByRole/getByLabel/getByText. Dois cuidados:
//  - o canvas fica por cima e "intercepta" o clique: clicamos nas coordenadas do elemento (page.mouse), nunca com locator.click();
//  - os campos de texto são <div contenteditable>: nada de fill(); clica e digita pelo teclado.
import AxeBuilder from "@axe-core/playwright";
import { expect, type Locator, type Page } from "@playwright/test";

export const SENHA_EXEMPLO = "teste123";
export const NUTRI_EXEMPLO = "nutri@exemplo.dev";

/** Abre a web e espera o Compose publicar a primeira árvore de semantics. */
export async function abrir(page: Page, caminho = "/") {
  await page.goto(caminho);
  await expect(page.getByRole("heading").first()).toBeVisible({ timeout: 60_000 });
}

async function caixa(loc: Locator) {
  await expect(loc.first()).toBeAttached({ timeout: 20_000 });
  const c = await loc.first().boundingBox();
  if (!c) throw new Error(`Sem posição para ${loc}`);
  return c;
}

/** Rola (roda do mouse sobre o canvas) até o elemento caber na janela e devolve o ponto central. */
async function trazer(page: Page, loc: Locator) {
  const vp = page.viewportSize()!;
  for (let i = 0; i < 25; i++) {
    const c = await caixa(loc);
    // Sem layout (caixa 0x0) é elemento fora da janela: o Compose só posiciona o que está perto da área visível.
    const semLayout = c.width === 0 && c.height === 0;
    const dentro = !semLayout && c.y >= 0 && c.y + c.height <= vp.height && c.x >= 0 && c.x + c.width <= vp.width;
    if (dentro) return { x: c.x + c.width / 2, y: c.y + c.height / 2 };
    // Folhas ancoradas embaixo rolam com a roda sobre elas; o ponto fica no meio da janela.
    await page.mouse.move(vp.width / 2, vp.height * 0.7);
    await page.mouse.wheel(0, !semLayout && c.y < 0 ? -300 : 300);
    await page.waitForTimeout(250);
  }
  throw new Error(`Não consegui trazer ${loc} para a janela`);
}

/** Espera o elemento parar de se mexer (mesma caixa em duas leituras seguidas) e devolve o ponto central.
 *  O Compose reposiciona a tela depois de um toque (ex.: ao sair do Resumo some a faixa de atalhos acima das abas e as abas sobem),
 *  e clicar na coordenada lida antes disso acerta outro botão (era o "Atualizar" no lugar da aba Evolução). */
async function pontoEstavel(page: Page, loc: Locator) {
  let ultimo = await trazer(page, loc);
  for (let i = 0; i < 20; i++) {
    await page.waitForTimeout(120);
    const atual = await trazer(page, loc);
    if (Math.abs(atual.x - ultimo.x) < 1 && Math.abs(atual.y - ultimo.y) < 1) return atual;
    ultimo = atual;
  }
  throw new Error(`${loc} não parou de mudar de posição`);
}

export async function clicar(page: Page, loc: Locator) {
  const { x, y } = await pontoEstavel(page, loc);
  await page.mouse.click(x, y);
  await page.waitForTimeout(150);
}

export const botao = (page: Page, nome: string | RegExp) => page.getByRole("button", { name: nome, exact: typeof nome === "string" });
export const campo = (page: Page, nome: string | RegExp) => page.getByRole("textbox", { name: nome, exact: typeof nome === "string" });

export async function digitar(page: Page, nome: string | RegExp, texto: string) {
  await clicar(page, campo(page, nome));
  await page.keyboard.type(texto, { delay: 5 });
}

/** Filtra a lista de pacientes pelo nome (substitui o que já estava no campo), para o cartão procurado ficar sozinho e o toque por coordenada não errar. */
export async function buscarPaciente(page: Page, texto: string) {
  await clicar(page, campo(page, /Buscar por nome/));
  await page.keyboard.press("Control+A");
  await page.keyboard.type(texto, { delay: 5 });
  // O filtro e o novo layout da lista levam um instante; clicar antes toca onde o cartão estava.
  await page.waitForTimeout(800);
}

export async function tocar(page: Page, nome: string | RegExp) {
  await clicar(page, botao(page, nome));
}

export async function veTexto(page: Page, texto: string | RegExp, timeout = 20_000) {
  await expect(page.getByText(texto).first()).toBeAttached({ timeout });
}

export async function entrar(page: Page, email: string, senha = SENHA_EXEMPLO) {
  await abrir(page);
  await digitar(page, "E-mail", email);
  await digitar(page, "Senha", senha);
  await tocar(page, "Entrar");
}

export async function sair(page: Page) {
  await tocar(page, "Sair");
  await expect(botao(page, "Entrar")).toBeAttached({ timeout: 20_000 });
}

/** Toca no botão até ele sair da árvore (diálogos animam ao abrir e um clique durante a animação erra o alvo). */
export async function tocarAteSumir(page: Page, nome: string | RegExp, tentativas = 6) {
  for (let i = 0; i < tentativas; i++) {
    if ((await botao(page, nome).count()) === 0) return;
    await page.waitForTimeout(500);
    if ((await botao(page, nome).count()) === 0) return;
    await clicar(page, botao(page, nome)).catch(() => {});
    await page.waitForTimeout(700);
  }
  await expect(botao(page, nome)).toHaveCount(0);
}

// ---- A6 (MOB-45): trava de acessibilidade, usada em acessibilidade.spec.ts e nas telas novas da 1.4.0 (MOB-97).
// As violações abaixo vêm do próprio Compose para web (1.12), não do código do app:
//  - aria-required-attr: o Compose emite role="heading" sem aria-level;
//  - aria-required-children: LazyColumn emite role="list" e os itens saem como botões, sem role="listitem";
//  - aria-roles: o <canvas> sai com role="generic".
const DO_COMPOSE = new Set(["aria-required-attr", "aria-required-children", "aria-roles"]);

/** Trava de acessibilidade (A6): axe-core sem violação nossa, ao menos um título e nenhum controle sem nome. */
export async function conferirAcessibilidade(page: Page, tela: string) {
  await page.waitForTimeout(1200);
  const { violations } = await new AxeBuilder({ page }).analyze();
  const nossas = violations.filter((v) => !DO_COMPOSE.has(v.id));
  expect(nossas.map((v) => `${v.id} (${v.nodes.length}): ${v.help}`), `axe em "${tela}"`).toEqual([]);
  // Toda tela tem ao menos um título (heading) e nenhum botão, campo ou aba fica sem nome acessível.
  const resumo = await page.evaluate(() => {
    const semNome: string[] = [];
    let titulos = 0;
    const visitar = (n: Element) => {
      if (n.shadowRoot) [...n.shadowRoot.children].forEach(visitar);
      const role = n.getAttribute("role");
      if (role === "heading") titulos++;
      if (role && ["button", "textbox", "tab", "checkbox", "switch", "link"].includes(role) && !(n.getAttribute("aria-label") || n.textContent || "").trim()) semNome.push(role);
      [...n.children].forEach(visitar);
    };
    visitar(document.body);
    return { titulos, semNome };
  });
  expect(resumo.titulos, `títulos em "${tela}"`).toBeGreaterThan(0);
  expect(resumo.semNome, `controles sem nome em "${tela}"`).toEqual([]);
}

/** Toca numa aba da ficha. A fileira de abas rola na horizontal: se a aba está fora da janela, rola a fileira com a roda (deltaX) sobre a aba "Resumo". */
export async function tocarAba(page: Page, nome: string) {
  const aba = botao(page, nome);
  const vp = page.viewportSize()!;
  for (let i = 0; i < 8; i++) {
    await expect(aba.first()).toBeAttached({ timeout: 20_000 });
    const c = await aba.first().boundingBox();
    if (c && c.width > 0 && c.x >= 0 && c.x + c.width <= vp.width) break;
    const ancora = await botao(page, "Resumo").first().boundingBox().catch(() => null);
    if (!ancora || ancora.width === 0) break;
    await page.mouse.move(ancora.x + ancora.width / 2, ancora.y + ancora.height / 2);
    await page.mouse.wheel(!c || c.width === 0 || c.x > 0 ? 400 : -400, 0);
    await page.waitForTimeout(250);
  }
  await clicar(page, aba);
}
