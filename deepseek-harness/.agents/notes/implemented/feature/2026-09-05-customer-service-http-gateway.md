# Agent Note: Customer-service HTTP gateway owns Agent session transport

Status: implemented

English | [中文](2026-09-05-customer-service-http-gateway.zh.md)

## Problem

The Java Backend needs a stable customer-service boundary, but the DSH Agent
runtime exposes session ownership, event logs, and streaming as in-process
services. Letting Backend depend on those internals would duplicate lifecycle
logic and make restart recovery or SSE ordering inconsistent.

## Decision

`@deepseek-ai/dsh-customer-service-gateway` is a host-side BFF mounted by the
customer-service composition. It owns two exact `POST` routes under one
configurable prefix: a blocking message route and an SSE streaming route. The
module validates a bounded JSON envelope, optionally authenticates the trusted
Backend bearer token, maps `conversation_id` to a DSH `SessionId`, resumes
persisted sessions when an id is supplied, and creates a new durable session
otherwise.

One managed session admits one turn at a time. The blocking response waits for
agent quiescence and projects the committed assistant message, usage, tool-call
outcomes, and handoff fact. The SSE response projects text deltas plus tool,
usage, done, and error events, then closes at the matching turn boundary. All
owned handles and route registrations are disposed with the plugin fiber.

The gateway never appends `user` or arbitrary `inputs` to the model prompt;
Backend remains the business and capability boundary. A blank service token is
accepted only for loopback development and keyless tests; deployments set the
token and the Java client forwards it.

## Alternatives considered

**Expose ACP over HTTP** — rejected. ACP is a stdio automation protocol with a
different session and transport lifecycle; adapting it here would leak protocol
state into the Java contract and make SSE cancellation ambiguous.

**Let Backend manage DSH sessions** — rejected. Session resume, event ordering,
turn serialization, and handle disposal are DSH runtime invariants and belong in
the BFF's deep module.

**Create a new agent for every request** — rejected. It loses durable
conversation history and makes `conversation_id` restart recovery impossible.

## Consequences

The Java adapter consumes a small provider-neutral HTTP contract and records DSH
usage/tool/handoff facts without depending on DSH package types. Local JSONL
persistence supports process restart recovery when the persistence root is
shared; multi-instance deployments still need a shared persistence backend and
must mount the same root or replace the persistence provider.

## Verification

The focused customer-service gate covers credential rejection, blocking answer
projection, SSE completion, and a real keyless `node:http` composition. The DSH
gateway and ACP application type-check together, and the Java Backend suite
passes with Flyway runtime dependencies resolved.
