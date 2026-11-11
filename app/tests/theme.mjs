// Run against the production bundle with a local Chrome/Chromium installation.
// CDP uses private pipes, so no debug endpoint or third-party test dependency is needed.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:http";
import { mkdtemp, readFile, rm, mkdir, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, extname } from "node:path";
import { fileURLToPath } from "node:url";

const dist = fileURLToPath(new URL("../dist/", import.meta.url));
const profile = await mkdtemp(join(tmpdir(), "llmd-theme-"));
const server = createServer(async (req, res) => {
  try {
    const pathname = new URL(req.url, "http://localhost").pathname;
    const path = pathname === "/" ? "index.html" : pathname.slice(1);
    if (path.includes("..")) throw new Error("Invalid path");
    const body = await readFile(join(dist, path));
    res.setHeader("Content-Type", { ".html": "text/html", ".js": "text/javascript", ".css": "text/css" }[extname(path)] ?? "application/octet-stream");
    res.end(body);
  } catch {
    res.writeHead(404).end();
  }
});
server.listen(0, "127.0.0.1");
await once(server, "listening");
const browser = spawn(process.env.CHROME_BIN ?? "google-chrome", [
  "--headless", "--no-sandbox", "--disable-gpu", "--remote-debugging-pipe",
  "--password-store=basic", "--use-mock-keychain", "--disable-extensions", "--disable-background-networking", "--no-first-run", "--no-proxy-server", "--disable-dev-shm-usage", `--user-data-dir=${profile}`,
], { stdio: ["ignore", "ignore", "ignore", "pipe", "pipe"] });
const pending = new Map();
let nextId = 0;
let buffer = "";
browser.stdio[4].on("data", chunk => {
  buffer += chunk;
  let end;
  while ((end = buffer.indexOf("\0")) !== -1) {
    const message = JSON.parse(buffer.slice(0, end));
    buffer = buffer.slice(end + 1);
    const request = pending.get(message.id);
    if (request) {
      pending.delete(message.id);
      clearTimeout(request.timer);
      if (message.error) request.reject(new Error(JSON.stringify(message.error)));
      else request.resolve(message.result);
    }
  }
});
function failPending(error) {
  for (const request of pending.values()) {
    clearTimeout(request.timer);
    request.reject(error);
  }
  pending.clear();
}
browser.on("error", failPending);
browser.on("exit", () => failPending(new Error("Browser exited")));
function send(method, params = {}, sessionId) {
  const id = ++nextId;
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      pending.delete(id);
      reject(new Error(`CDP timeout: ${method}`));
    }, 15000);
    pending.set(id, { resolve, reject, timer });
    browser.stdio[3].write(JSON.stringify({ id, method, params, sessionId }) + "\0");
  });
}

const fixture = `
  const inventory = {models: ["theme-fixture"], modelPaths: {"theme-fixture": "/models/theme-fixture.litertlm"}};
  window.llmdAndroid = {
    getModelState: () => JSON.stringify(inventory),
    getHealthState: () => JSON.stringify({serviceState: "off", notificationsEnabled: false}),
    importModel: () => window.dispatchEvent(new CustomEvent("llmd-models-changed", {
      detail: {...inventory, error: "Synthetic import error"}
    })),
    deleteModel() {}, setServiceEnabled() {}, openBackgroundSettings() {}
  };
`;

// Inspect actual computed text colors against composited ancestor backgrounds.
// Even large text is held to 4.5:1; disabled controls are exempt.
function inspectContrast() {
  const parse = color => color.match(/[\d.]+/g).map(Number);
  const over = (top, bottom) => {
    const alpha = top[3] ?? 1;
    return top.slice(0, 3).map((channel, i) => channel * alpha + bottom[i] * (1 - alpha));
  };
  const luminance = rgb => rgb.map(v => {
    const c = v / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  }).reduce((sum, c, i) => sum + c * [0.2126, 0.7152, 0.0722][i], 0);
  const failures = [];
  for (const element of document.querySelectorAll("body *")) {
    if (!element.getClientRects().length || element.closest(":disabled")) continue;
    if (element.tagName !== "SELECT" && ![...element.childNodes].some(n => n.nodeType === Node.TEXT_NODE && n.textContent.trim())) continue;
    const ancestors = [];
    for (let node = element; node; node = node.parentElement) ancestors.unshift(node);
    let background = [255, 255, 255];
    for (const node of ancestors) background = over(parse(getComputedStyle(node).backgroundColor), background);
    const foreground = over(parse(getComputedStyle(element).color), background);
    const values = [luminance(background), luminance(foreground)].sort((a, b) => a - b);
    const ratio = (values[1] + 0.05) / (values[0] + 0.05);
    if (ratio < 4.5) failures.push({text: element.textContent.trim().slice(0, 60), ratio});
  }
  return failures;
}

try {
  const { targetId } = await send("Target.createTarget", { url: "about:blank" });
  const { sessionId } = await send("Target.attachToTarget", { targetId, flatten: true });
  const call = (method, params) => send(method, params, sessionId);
  async function evaluate(expression) {
    const result = await call("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
    assert.equal(result.exceptionDetails, undefined, JSON.stringify(result.exceptionDetails));
    return result.result.value;
  }
  async function ready() {
    await evaluate(`new Promise((resolve, reject) => {
      const deadline = Date.now() + 5000;
      function check() {
        if (document.querySelector("#app h1") && !document.querySelector(".spinner") && !document.querySelector("#check-health:disabled")) resolve();
        else if (Date.now() > deadline) reject(new Error("Page did not render"));
        else requestAnimationFrame(check);
      }
      check();
    })`);
  }
  async function check(theme) {
    await evaluate("new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))");
    assert.equal(await evaluate("getComputedStyle(document.documentElement).colorScheme"), theme);
    assert.equal(await evaluate("getComputedStyle(document.documentElement).backgroundColor"),
      theme === "dark" ? "rgb(16, 21, 34)" : "rgb(246, 247, 251)");
    assert.deepEqual(await evaluate(`(${inspectContrast})()`), []);
  }
  await call("Page.enable");
  for (const host of ["desktop", "android"]) {
    const desktopFixture = `
      window.fetch = async () => ({ok: true, json: async () => ({data: [{id: "theme-fixture", owned_by: "litert-lm"}]})});
      window.__TAURI_INTERNALS__ = {invoke: async command => {
        if (command === "health") return {status: "ok"};
        throw new Error("Synthetic import error");
      }};
      window.prompt = () => "/models/theme-fixture.litertlm";
    `;
    const {identifier} = await call("Page.addScriptToEvaluateOnNewDocument", { source: host === "android" ? fixture : desktopFixture });
    for (const width of [1100, 390]) {
      await call("Emulation.setDeviceMetricsOverride", { width, height: 900, deviceScaleFactor: 1, mobile: width < 720 });
      for (const initial of ["light", "dark"]) {
        await call("Emulation.setEmulatedMedia", { features: [{ name: "prefers-color-scheme", value: initial }] });
        for (const route of ["status", "models", "models/theme-fixture", "logs"]) {
          await call("Page.navigate", { url: `http://127.0.0.1:${server.address().port}/?case=${host}-${width}-${initial}-${encodeURIComponent(route)}#/${route}` });
          await ready();
          const heading = route === "status" ? "Service status" : route === "models" ? "Models" : route === "logs" ? "Logs" : "theme-fixture";
          assert.equal(await evaluate('document.querySelector("#app h1").textContent'), heading);
          await check(initial);
          // Both runtime directions without navigation or a UI rerender.
          const opposite = initial === "light" ? "dark" : "light";
          for (const theme of [opposite, initial]) {
            await call("Emulation.setEmulatedMedia", { features: [{ name: "prefers-color-scheme", value: theme }] });
            await check(theme);
          }
          for (const selector of [".button.primary", ".button.secondary", ".button.danger", ".button.danger-outline", ".model-row", "nav a", "select", "input"]) {
            const point = await evaluate(`(() => {
              const element = document.querySelector(${JSON.stringify(selector)});
              if (!element || element.disabled) return null;
              element.scrollIntoView({block: "center"});
              element.focus();
              const rect = element.getBoundingClientRect();
              return {x: rect.x + rect.width / 2, y: rect.y + rect.height / 2};
            })()`);
            if (!point) continue;
            assert.equal(await evaluate('getComputedStyle(document.activeElement).outlineStyle'), "solid");
            await call("Input.dispatchMouseEvent", { type: "mouseMoved", ...point });
            await evaluate("new Promise(resolve => setTimeout(resolve, 200))");
            await check(initial);
          }
          if (process.env.THEME_SCREENSHOT_DIR && width === 1100 && host === "android" && ["status", "models/theme-fixture"].includes(route)) {
            await mkdir(process.env.THEME_SCREENSHOT_DIR, {recursive: true});
            await evaluate("window.scrollTo(0, 0)");
            const {data} = await call("Page.captureScreenshot", {format: "png", captureBeyondViewport: true});
            await writeFile(join(process.env.THEME_SCREENSHOT_DIR, `${initial}-${route.replaceAll("/", "-")}.png`), Buffer.from(data, "base64"));
          }
          // Re-enter a mounted page through the same hash navigation used by the UI.
          await evaluate('window.location.hash = "#/status"');
          await evaluate('new Promise(resolve => requestAnimationFrame(resolve))');
          await evaluate(`window.location.hash = ${JSON.stringify("#/" + route)}`);
          await evaluate('new Promise(resolve => requestAnimationFrame(resolve))');
          assert.equal(await evaluate('document.querySelector("#app h1").textContent'), heading);
          await check(initial);
          if (route === "models") {
            await evaluate('document.querySelector("#import-model").click()');
            assert.equal(await evaluate('document.querySelector(".mutation-error")?.textContent.includes("Synthetic import error")'), true);
            await check(initial);
            if (host === "android") {
              // Inventory errors and empty inventory use different rendered surfaces.
              await evaluate('window.llmdAndroid.getModelState = () => { throw new Error("Synthetic inventory error"); }; document.querySelector("#refresh-models").click()');
              await ready();
              assert.equal(await evaluate('Boolean(document.querySelector(".error-state"))'), true);
              await check(initial);
              await evaluate('window.llmdAndroid.getModelState = () => JSON.stringify({models: [], modelPaths: {}}); document.querySelector("#refresh-models").click()');
              await ready();
              assert.equal(await evaluate('document.querySelector(".empty-state h2").textContent'), "No models imported");
              await check(initial);
            }
          }
          console.log(`PASS ${host} ${width}px ${initial} ${route}: initial, live bidirectional, re-entry, focus, hover, contrast`);
        }
      }
    }
    await call("Page.removeScriptToEvaluateOnNewDocument", {identifier});
  }
} finally {
  if (browser.exitCode === null) {
    const exited = once(browser, "exit");
    await send("Browser.close");
    await exited;
  }
  await new Promise(resolve => server.close(resolve));
  await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
