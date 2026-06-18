package no.novari.flyt.audit.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.metrics.AuditMetricsListener
import org.hibernate.annotations.Type
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class, AuditMetricsListener::class)
abstract class CreatedAuditedEntity {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @CreatedBy
    @Type(JsonType::class)
    @Column(name = "created_by", columnDefinition = "jsonb", nullable = false, updatable = false)
    lateinit var createdBy: Actor
        protected set
}
