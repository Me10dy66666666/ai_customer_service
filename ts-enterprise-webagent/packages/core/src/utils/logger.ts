import type { Logger } from "../adapters/contracts.js";

export const consoleLogger: Logger = {
  info(message, context) {
    console.info(JSON.stringify({ level: "info", message, context }));
  },
  warn(message, context) {
    console.warn(JSON.stringify({ level: "warn", message, context }));
  },
  error(message, context) {
    console.error(JSON.stringify({ level: "error", message, context }));
  }
};
