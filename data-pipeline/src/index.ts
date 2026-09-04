import { loadPipelineConfig } from "./config.js";
import { buildApp } from "./app.js";

async function main(): Promise<void> {
  const config = loadPipelineConfig();
  const app = await buildApp({ config });

  await app.listen({ port: config.port, host: "0.0.0.0" });
  console.info(`data-pipeline listening on :${config.port}`);
}

main().catch((error: unknown) => {
  console.error("data-pipeline failed to start:", error);
  process.exit(1);
});
