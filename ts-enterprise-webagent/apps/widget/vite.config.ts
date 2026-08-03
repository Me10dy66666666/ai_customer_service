import { defineConfig } from "vite";

export default defineConfig({
  build: {
    lib: {
      entry: "src/index.ts",
      name: "WebAgentWidget",
      fileName: () => "web-agent-widget.js",
      formats: ["es"],
    },
    outDir: "dist",
    sourcemap: true,
    minify: "esbuild",
  },
});
