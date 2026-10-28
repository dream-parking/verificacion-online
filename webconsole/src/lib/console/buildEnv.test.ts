import { describe, expect, it } from "vitest";
import { assertApiUrl } from "./buildEnv";

const DEV_URL = "https://api.dev.identidad.alambritos.online";

describe("assertApiUrl", () => {
  it("un build de Dev o QA con la URL del API pasa", () => {
    expect(() => assertApiUrl({ DEPLOY_ENV: "dev", NEXT_PUBLIC_API_URL: DEV_URL })).not.toThrow();
    expect(() => assertApiUrl({ DEPLOY_ENV: "qa", NEXT_PUBLIC_API_URL: DEV_URL })).not.toThrow();
  });

  it("un build de Dev sin la URL falla y dice qué falta y dónde definirla", () => {
    expect(() => assertApiUrl({ DEPLOY_ENV: "dev" })).toThrow(/Falta NEXT_PUBLIC_API_URL en el build de dev/);
    expect(() => assertApiUrl({ DEPLOY_ENV: "dev" })).toThrow(/webconsole-deploy\.yml/);
  });

  it("un build de QA sin la URL falla nombrando QA", () => {
    expect(() => assertApiUrl({ DEPLOY_ENV: "qa" })).toThrow(/build de qa/);
  });

  it.each(["", "   "])("una URL vacía (%j) cuenta como faltante", (url) => {
    expect(() => assertApiUrl({ DEPLOY_ENV: "dev", NEXT_PUBLIC_API_URL: url })).toThrow(/Falta NEXT_PUBLIC_API_URL/);
  });

  it.each(["http://localhost:8080", "http://127.0.0.1:8080/api", "https://localhost", "http://[::1]:8080"])(
    "una URL local (%s) en un build de Dev falla",
    (url) => {
      expect(() => assertApiUrl({ DEPLOY_ENV: "dev", NEXT_PUBLIC_API_URL: url })).toThrow(/máquina local/);
    },
  );

  it("un dominio que solo empieza con localhost no se confunde con uno local", () => {
    expect(() => assertApiUrl({ DEPLOY_ENV: "dev", NEXT_PUBLIC_API_URL: "https://localhost.example.com" })).not.toThrow();
  });

  it("sin DEPLOY_ENV (desarrollo local y el build del CI) no se revisa nada", () => {
    expect(() => assertApiUrl({})).not.toThrow();
    expect(() => assertApiUrl({ NEXT_PUBLIC_API_URL: "http://localhost:8080" })).not.toThrow();
    expect(() => assertApiUrl({ DEPLOY_ENV: "  " })).not.toThrow();
  });
});
