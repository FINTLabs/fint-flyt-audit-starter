package no.novari.flyt.audit.metrics

import io.micrometer.core.instrument.MeterRegistry

class AuditMetrics(
    private val registry: MeterRegistry,
) {
    fun recordWrite(entityName: String) {
        registry.counter(WRITE_COUNTER, "entity", entityName).increment()
    }

    companion object {
        // Prometheus-navn: fint_flyt_audit_write_total (Micrometer legger til _total for Counter)
        const val WRITE_COUNTER = "fint_flyt_audit_write"
    }
}
