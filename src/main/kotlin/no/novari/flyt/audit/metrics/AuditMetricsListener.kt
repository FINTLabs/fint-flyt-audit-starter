package no.novari.flyt.audit.metrics

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.springframework.beans.factory.ObjectProvider

/** JPA entity listener created by Hibernate through Spring's bean container. Records nothing without [AuditMetrics]. */
class AuditMetricsListener(
    private val metrics: ObjectProvider<AuditMetrics>,
) {
    @PostPersist
    @PostUpdate
    @PostRemove
    fun afterWrite(entity: Any) {
        metrics.ifAvailable?.recordWrite(entity.javaClass.simpleName)
    }
}
