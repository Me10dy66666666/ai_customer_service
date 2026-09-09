import { assertProductionConfig, loadConfig } from "./config.js";
import { createRuntimeServer } from "./server.js";

const config = loadConfig();
if (process.env.NODE_ENV === "production") {
  assertProductionConfig(config);
}

const server = createRuntimeServer(config);
server.listen(config.port, "0.0.0.0", () => {
  console.log(`Pi Agent Runtime listening on :${config.port}`);
});
