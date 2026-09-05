# customer-service example

Minimal headless customer-service agent: the DeepSeek adapter, the `search_knowledge` tool
(bridging to the data-pipeline vector search), and the ACP automation app (agent spine + JSONL
persistence + ACP bridge).

## Run

```sh
DEEPSEEK_API_KEY=... DSH_GATEWAY_SERVICE_TOKEN=... PIPELINE_SERVICE_TOKEN=... \
AGENT_CAPABILITY_TOKEN=... DATA_PIPELINE_URL=http://localhost:3002 \
BACKEND_BASE_URL=http://localhost:8081 \
pnpm dsh --profile customer-service
```

or via the ACP demo entry. Requires the data-pipeline service to be running (see
`../../../data-pipeline`).

## Security boundary

- Model-visible tools contain no user or agent identity parameters. The host injects a five-minute
  Java Backend capability token scoped to the user, session, and allowed tools.
- Knowledge search uses a separate data-pipeline service token.
- Network tools declare five-second cooperative deadlines; the composition also enforces six steps
  and eight tool calls per turn. Work-order creation produces a proposal requiring UI confirmation.
- Focused gate: `node node_modules/vitest/vitest.mjs run --config examples/customer-service/vitest.config.ts`.
