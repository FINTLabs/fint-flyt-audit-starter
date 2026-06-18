package no.novari.flyt.audit.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuditMetricsTest {
    private val registry = SimpleMeterRegistry()
    private val metrics = AuditMetrics(registry)

    @Test
    fun `recordSuccess inkrementerer success-teller`() {
        metrics.recordSuccess("SomeEntity")
        val count =
            registry
                .counter(AuditMetrics.WRITE_COUNTER, "entity", "SomeEntity", "outcome", "success")
                .count()
        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `recordFailure inkrementerer failure-teller`() {
        metrics.recordFailure("SomeEntity")
        val count =
            registry
                .counter(AuditMetrics.WRITE_COUNTER, "entity", "SomeEntity", "outcome", "failure")
                .count()
        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `startTimer og stopTimer registrerer én timer-observasjon`() {
        val sample = metrics.startTimer()
        metrics.stopTimer(sample, "SomeEntity")
        val count =
            registry
                .timer(AuditMetrics.WRITE_DURATION, "entity", "SomeEntity")
                .count()
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `success- og failure-tellere er uavhengige per entity`() {
        metrics.recordSuccess("EntityA")
        metrics.recordSuccess("EntityA")
        metrics.recordFailure("EntityA")
        metrics.recordSuccess("EntityB")

        val aSuccess = registry.counter(AuditMetrics.WRITE_COUNTER, "entity", "EntityA", "outcome", "success").count()
        val aFailure = registry.counter(AuditMetrics.WRITE_COUNTER, "entity", "EntityA", "outcome", "failure").count()
        val bSuccess = registry.counter(AuditMetrics.WRITE_COUNTER, "entity", "EntityB", "outcome", "success").count()

        assertThat(aSuccess).isEqualTo(2.0)
        assertThat(aFailure).isEqualTo(1.0)
        assertThat(bSuccess).isEqualTo(1.0)
    }
}
