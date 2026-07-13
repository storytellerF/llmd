import { invoke } from "@tauri-apps/api/core";
import "./styles.css";

const output = document.querySelector<HTMLPreElement>("#output");
const health = document.querySelector<HTMLButtonElement>("#health");
const apiBase = document.querySelector<HTMLSpanElement>("#api-base");
const modelPath = document.querySelector<HTMLSpanElement>("#model-path");
const logs = document.querySelector<HTMLAnchorElement>("#logs");

health?.addEventListener("click", async () => {
  try {
    const response = await invoke<Record<string, unknown>>("health");
    if (output) output.textContent = JSON.stringify(response, null, 2);
  } catch (error) {
    if (output) output.textContent = String(error);
  }
});

const defaultApiBase = "http://127.0.0.1:11435";
const defaultModelPath = "/data/local/tmp/llmd/gemma-4-E2B-it.litertlm";

if (apiBase) apiBase.textContent = defaultApiBase;
if (modelPath) modelPath.textContent = defaultModelPath;
if (logs) logs.href = `${defaultApiBase}/logs`;
