# @deepseek-ai/dsh-tool-search-knowledge

Customer-service `search_knowledge` tool. On invocation it POSTs to the
[data-pipeline](../../../data-pipeline) `/search` endpoint and returns the matching knowledge
sources (id / title / excerpt / sourceType) as the canonical value, rendered as a cited list.

## Configuration

| Key | Required | Description |
|---|---|---|
| `dataPipelineBaseUrl` | yes | Base URL of the data-pipeline service, e.g. `http://localhost:3002` |
| `serviceToken` | yes | Bearer credential accepted by the data-pipeline service |
| `timeoutMs` | yes | Cooperative HTTP deadline; recommended `5000` |

The tool calls `POST {dataPipelineBaseUrl}/search` with `{ query, limit }` and expects
`{ sources: [{ id, title, excerpt, sourceType, metadata }] }`. The `metadata` field is dropped from
the model-facing canonical value.

## Usage

Compose it into a `cordis.yml` alongside the DeepSeek adapter and an agent spine (see
`examples/customer-service`). The `dataPipelineBaseUrl` is typically supplied from the environment:

```yaml
- id: search-knowledge
  name: '@deepseek-ai/dsh-tool-search-knowledge'
  config:
    dataPipelineBaseUrl: !!js process.env.DATA_PIPELINE_URL ?? 'http://localhost:3002'
    serviceToken: !!js process.env.PIPELINE_SERVICE_TOKEN
    timeoutMs: 5000
```
