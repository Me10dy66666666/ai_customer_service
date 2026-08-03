export class CustomerAgentError extends Error {
  public readonly code: string;

  public constructor(code: string, message: string, options?: { cause?: unknown }) {
    super(message, options);
    this.name = "CustomerAgentError";
    this.code = code;
  }
}
