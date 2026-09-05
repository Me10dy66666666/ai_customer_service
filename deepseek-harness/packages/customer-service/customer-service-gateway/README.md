# @deepseek-ai/dsh-customer-service-gateway

The customer-service BFF that sits between the Java Backend and the DSH Agent
runtime. It exposes two narrow routes:

- `POST /api/v1/customer-service/messages` returns a committed assistant answer;
- `POST /api/v1/customer-service/messages/streaming` returns SSE `token`, `message`,
  `done`, and `error` events. Text frames include `conversation_id` so the Java
  adapter can normalize them to its existing `{ event: "message", answer }`
  streaming port without leaking DSH-specific event names into the domain layer.

Both routes accept the Backend envelope `{ query, user, conversation_id, inputs,
response_mode }`. `user` and `inputs` are host metadata; they are not appended to
the model prompt. `conversation_id` is the durable DSH session id. A supplied id
is resumed from the configured session persistence provider, while an empty id
creates a new session.

The route requires a non-empty `serviceToken` and a matching
`Authorization: Bearer ...` header. Backend remains the business and capability
source of truth; this plugin only owns Agent lifecycle and transport.

When `customerServiceBackendBaseUrl` is non-empty, a request with
`X-Agent-Capability-Token` mounts the customer-service tools. The Gateway passes
that credential only to the tools composed inside the new or resumed session,
binds it to the conversation, and rejects a later request with a different
credential. Requests without a capability remain knowledge-only, which allows
guest sessions to use public retrieval without exposing business tools.
`customerServiceCapabilityToken` is a local-only fallback for callers that
cannot forward a header; production Backend requests use the per-session header.

The scoped tools are `lookup_order` and `create_work_order`. The latter creates
only a customer-owned proposal; Java performs the final work-order mutation
after an authenticated product-user confirmation.

The module serializes turns per conversation, bounds JSON bodies, and disposes all
owned Agent handles with the route registration. It requires `agents` and
`webServer` services and is normally composed by the customer-service example.
