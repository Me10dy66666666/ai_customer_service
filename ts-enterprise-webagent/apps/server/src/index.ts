import { buildApp } from "./app.js";
import { loadServerConfig } from "./config.js";

async function bootstrap(): Promise<void> {
  const config = loadServerConfig();
  const app = await buildApp({ config });

  try {
    await app.listen({
      port: config.port,
      host: "0.0.0.0"
    });
    console.info(`Server listening on http://localhost:${config.port}`);
  } catch (error) {
    console.error("Failed to start server", error);
    process.exitCode = 1;
  }
}

void bootstrap();
