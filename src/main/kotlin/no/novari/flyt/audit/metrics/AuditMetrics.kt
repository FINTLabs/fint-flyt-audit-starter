package no.novari.flyt.audit.metrics

import io.micrometer.core.instrument.MeterRegistry

/**
 * Micrometer metrics for JPA writes.
 *
 * Registered by `FlytAuditAutoConfiguration` when a `MeterRegistry` bean exists.
 *
 * Metrics:
 * - `fint_flyt_audit_write_total`: counter per entity (tag `entity=<class name>`).
 *   Micrometer adds the Prometheus suffix `_total`.
 */
class AuditMetrics(
    private val registry: MeterRegistry,
) {
    fun recordWrite(entityName: String) {
        registry.counter(WRITE_COUNTER, "entity", entityName).increment()
    }

    companion object {
        const val WRITE_COUNTER = "fint_flyt_audit_write"
    }
}
