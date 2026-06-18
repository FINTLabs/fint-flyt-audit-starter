package no.novari.flyt.audit.metrics

import io.micrometer.core.instrument.Timer
import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import jakarta.persistence.PrePersist
import jakarta.persistence.PreRemove
import jakarta.persistence.PreUpdate
import no.novari.flyt.audit.config.ApplicationContextHolder
import org.springframework.beans.factory.getBean

class AuditMetricsListener {
    private val samples = ThreadLocal<Timer.Sample>()

    @PrePersist
    @PreUpdate
    @PreRemove
    fun beforeWrite(entity: Any) {
        runCatching { samples.set(metrics().startTimer()) }
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    fun afterWrite(entity: Any) {
        runCatching {
            val m = metrics()
            val entityName = entity.javaClass.simpleName
            samples.get()?.let { m.stopTimer(it, entityName) }
            samples.remove()
            m.recordSuccess(entityName)
        }
    }

    private fun metrics() = ApplicationContextHolder.getContext().getBean<AuditMetrics>()
}
