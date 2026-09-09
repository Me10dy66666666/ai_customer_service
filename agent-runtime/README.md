# Pi Agent Runtime

This service owns the Pi agent loop, model streaming, tool selection, and runtime session transcript. Java remains the source of truth for identity, authorization, order data, work-order proposals, confirmations, and durable writes.

The runtime never connects to the database. Agent tools call the Java capability gateway, and knowledge search calls the data-pipeline service with its service token.

## Run

```powershell
npm install
$env:PI_RUNTIME_SERVICE_TOKEN = "local-dev-pi-token"
$env:BACKEND_BASE_URL = "http://localhost:8081"
$env:DATA_PIPELINE_URL = "http://localhost:3002"
$env:PIPELINE_SERVICE_TOKEN = "local-dev-pipeline-token"
$env:MODEL_PROVIDER = "deepseek"
$env:MODEL_NAME = "deepseek-v4-pro"
$env:MODEL_API_KEY = "..."
npm run build
npm start
```

The BFF contract is compatible with the Java chat application:

- `GET /health`
- `POST /api/v1/customer-service/messages`
- `POST /api/v1/customer-service/messages/streaming` (SSE)

Every message request must include the Pi service token and a Java-issued `X-Agent-Capability-Token`. The runtime forwards that capability and the business session ID to the Java tool gateway; model-visible arguments never contain a user ID.

## Configuration

`MODEL_PROVIDER` currently supports `deepseek` and `openai` through Pi's official provider adapters. `MODEL_BASE_URL` may override the provider catalog endpoint for an OpenAI-compatible deployment. `PI_RUNTIME_MAX_SESSIONS` bounds the in-memory runtime-session cache; durable business sessions remain in Java.
