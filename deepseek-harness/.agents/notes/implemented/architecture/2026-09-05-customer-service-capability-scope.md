# Agent Note: Customer-service tools are composed per session and bound to a capability token

Status: implemented

English | [中文](2026-09-05-customer-service-capability-scope.zh.md)

## Problem

The customer-service Agent crosses a trusted Java business boundary. A deployment-wide tool plugin cannot safely represent the authenticated customer for every concurrent session: its bearer credential would be shared, and a model-visible argument could become an identity selector. Work-order creation also changes durable business state and requires an explicit customer decision rather than an inferred model intent.

## Decision

The customer-service Gateway composes the business-tool Consumer inside each agent scope at session creation or resume. The host passes a short-lived capability token received from the trusted Java gateway; the token is retained only in the tool closure and the managed session record, never in model messages, prompts, tool schemas, or telemetry values.

The Gateway requires one capability token when its Java backend URL is configured, binds that token to the conversation, and rejects a later request that presents a different token. Concurrent requests without a conversation id receive distinct session identities. The existing gateway service token remains a separate transport credential between Java and DSH.

The Java Agent Tool Gateway verifies the capability JWT and resolves its subject from the server-side user record. `lookup_order` reads only that subject's orders. `create_work_order` calls a proposal command that stores a short-lived Redis record containing the verified user and session; it does not invoke the work-order domain mutation. A product-user JWT with `USER` or `VIP` role must call the confirmation endpoint. Confirmation verifies ownership, atomically consumes the proposal with `getAndDelete`, and delegates creation to `WorkOrderApplicationService`.

The package keeps the DSH [Service Definition / Service Provider / Consumer capability-seam vocabulary](2026-06-13-capability-seams.md): `tool-customer-service` owns model-facing schemas and HTTP execution, while the Gateway is the scoped composition owner. The tool package still exports `apply` for simple static compositions, and its registration function is the explicit seam for scoped hosts.

## Alternatives considered

- **One global customer-service tool instance with a deployment token** — rejected because the credential cannot distinguish concurrent customers and makes cross-user access a deployment-level accident.
- **Let the model provide `userId` or an authorization header** — rejected because model output is untrusted data; authorization identity must come from the authenticated Java request.
- **Let the Agent create a work order directly** — rejected because a model tool call is not customer confirmation and a retried turn could duplicate a durable mutation.
- **Store proposals only in process memory** — rejected because restart and multi-instance operation would lose the confirmation record; Redis supplies a shared TTL store and atomic one-time consumption.

## Consequences

Each session receives an isolated business-tool closure and a stable authorization identity, so the model sees the same narrow schemas while the host controls who the tools act for. Java remains the business source of truth and owns the final mutation. The extra confirmation round and Redis state are intentional product and operational costs. Static local DSH compositions can still use the exported `apply`, but production wiring must provide a session-scoped capability token.

## Testing

The Gateway test composes the real customer-tool registration function in a fake agent scope, accepts the first capability for a session, and rejects a second capability for the same conversation. Java controller tests verify that a capability can propose only, that a different authenticated user cannot confirm it, and that the confirmed proposal is consumed before the domain service is called. Host library compilation and TypeScript type checking cover the cross-package setup contract.
