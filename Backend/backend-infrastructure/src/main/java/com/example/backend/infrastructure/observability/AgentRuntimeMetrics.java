package com.example.backend.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Central metric vocabulary shared by the Java BFF and DSH integration boundary. */
@Component
public class AgentRuntimeMetrics {

    private final MeterRegistry registry;

    public AgentRuntimeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startRequest() {
        return Timer.start(registry);
    }

    public void finishRequest(Timer.Sample sample, String provider, String mode, String outcome) {
        sample.stop(Timer.builder("customer.agent.request.duration")
                .description("Customer Agent request duration")
                .tag("provider", provider)
                .tag("mode", mode)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry));
        Counter.builder("customer.agent.requests")
                .description("Customer Agent request count")
                .tags("provider", provider, "mode", mode, "outcome", outcome)
                .register(registry)
                .increment();
    }

    public void recordFirstToken(String provider, Duration duration) {
        Timer.builder("customer.agent.first.token")
                .description("Time to first response token")
                .tag("provider", provider)
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }

    public void recordTokenUsage(String provider, String model, String tokenType, long tokens) {
        DistributionSummary.builder("customer.agent.tokens")
                .baseUnit("tokens")
                .tags("provider", provider, "model", model, "type", tokenType)
                .register(registry)
                .record(Math.max(0, tokens));
    }

    public void recordCost(String provider, String model, double amount) {
        DistributionSummary.builder("customer.agent.cost")
                .baseUnit("currency_units")
                .tags("provider", provider, "model", model)
                .register(registry)
                .record(Math.max(0, amount));
    }

    public void recordToolCall(String tool, String outcome) {
        Counter.builder("customer.agent.tool.calls")
                .tags("tool", tool, "outcome", outcome)
                .register(registry)
                .increment();
    }

    public void recordHumanHandoff(String reason) {
        Counter.builder("customer.agent.handoffs")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    public void recordAuthorizationBlocked(String boundary) {
        Counter.builder("customer.agent.authorization.blocked")
                .tag("boundary", boundary)
                .register(registry)
                .increment();
    }
}
