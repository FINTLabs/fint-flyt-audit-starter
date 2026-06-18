package no.novari.flyt.audit.metrics

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import no.novari.flyt.audit.config.ApplicationContextHolder
import org.springframework.beans.factory.getBean

class AuditMetricsListener {
    private val metrics: AuditMetrics? by lazy {
        runCatching { ApplicationContextHolder.getContext().getBean<AuditMetrics>() }.getOrNull()
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    fun afterWrite(entity: Any) {
        metrics?.recordWrite(entity.javaClass.simpleName)
    }
}
