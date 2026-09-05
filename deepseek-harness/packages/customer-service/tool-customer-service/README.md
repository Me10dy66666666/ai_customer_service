# @deepseek-ai/dsh-tool-customer-service

Customer-service business tools bridging to the Java backend REST API. Registers two tools:

| Tool | Backend | Purpose |
|---|---|---|
| `lookup_order` | `GET /api/agent/tools/orders` | List the capability subject's historical orders |
| `create_work_order` | `POST /api/agent/tools/work-orders/proposals` | Create a proposal requiring customer confirmation |

Both unwrap the backend `Result<T>` envelope (`{ code, message, data }`, success when `code === 200`).

## Configuration

| Key | Required | Description |
|---|---|---|
| `backendBaseUrl` | yes | Base URL of the Java backend, e.g. `http://localhost:8081` |
| `capabilityToken` | yes | Short-lived token scoped to one user, session, and tool set |
| `timeoutMs` | yes | Cooperative HTTP deadline; recommended `5000` |

Identity is deliberately absent from both model-visible tool schemas. For a shared multi-session
ACP process, mount the tools in a per-agent scope with that session's capability; the example's
environment token is suitable only for an isolated single-customer pilot worker.

## Deferred

- `transfer_to_human` — the backend triggers transfer via `AgentSessionService.transferToHuman`
  (WebSocket path), with no dedicated REST endpoint yet; needs a small backend endpoint before it
  can be exposed as a tool.
- `check_sla_policy` — the current `GET /api/sla-config` is admin-only CRUD, not a customer-facing
  policy check; deferred until an appropriate query endpoint exists.
