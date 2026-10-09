import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  resolve: { tsconfigPaths: true }, // alias @/* de tsconfig.json
  test: {
    environment: "jsdom",
    // Sin tests todavía: que la falta de cobertura la marque el CI, no un "No test files found".
    passWithNoTests: true,
    coverage: {
      provider: "v8",
      // Todo src cuenta, aunque ningún test lo importe; si no, la cobertura sale inflada.
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.{test,spec}.{ts,tsx}"],
      reporter: ["text-summary", "lcov"], // coverage/lcov.info lo leen el CI y Sonar
    },
  },
});
