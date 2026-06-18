package no.novari.flyt.audit.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuditMetricsTest {
    private val registry = SimpleMeterRegistry()
    private val metrics = AuditMetrics(registry)

    @Test
    fun `recordWrite inkrementerer skriveteller for entitet`() {
        metrics.recordWrite("SomeEntity")
        val count =
            registry
                .counter(AuditMetrics.WRITE_COUNTER, "entity", "SomeEntity")
                .count()
        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `tellere er uavhengige per entity`() {
        metrics.recordWrite("EntityA")
        metrics.recordWrite("EntityA")
        metrics.recordWrite("EntityB")

        val aCount = registry.counter(AuditMetrics.WRITE_COUNTER, "entity", "EntityA").count()
        val bCount = registry.counter(AuditMetrics.WRITE_COUNTER, "entity", "EntityB").count()

        assertThat(aCount).isEqualTo(2.0)
        assertThat(bCount).isEqualTo(1.0)
    }
}
