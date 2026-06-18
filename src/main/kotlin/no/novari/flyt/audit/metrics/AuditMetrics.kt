package no.novari.flyt.audit.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer

class AuditMetrics(
    private val registry: MeterRegistry,
) {
    fun recordSuccess(entityName: String) {
        registry.counter(WRITE_COUNTER, "entity", entityName, "outcome", "success").increment()
    }

    fun recordFailure(entityName: String) {
        registry.counter(WRITE_COUNTER, "entity", entityName, "outcome", "failure").increment()
    }

    fun startTimer(): Timer.Sample = Timer.start(registry)

    fun stopTimer(
        sample: Timer.Sample,
        entityName: String,
    ) {
        sample.stop(
            Timer
                .builder(WRITE_DURATION)
                .tag("entity", entityName)
                .register(registry),
        )
    }

    companion object {
        // Prometheus-navn: fint_flyt_audit_write_total (Micrometer legger til _total for Counter)
        const val WRITE_COUNTER = "fint_flyt_audit_write"

        // Prometheus-navn: fint_flyt_audit_write_duration_seconds_* (Micrometer legger til _seconds for Timer)
        const val WRITE_DURATION = "fint_flyt_audit_write_duration"
    }
}
