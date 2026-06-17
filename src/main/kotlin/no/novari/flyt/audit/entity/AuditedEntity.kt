package no.novari.flyt.audit.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import no.novari.flyt.audit.actor.Actor
import org.hibernate.annotations.Type
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import java.time.Instant

@MappedSuperclass
abstract class AuditedEntity : CreatedAuditedEntity() {
    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    lateinit var lastModifiedAt: Instant
        protected set

    @LastModifiedBy
    @Type(JsonType::class)
    @Column(name = "last_modified_by", columnDefinition = "jsonb", nullable = false)
    lateinit var lastModifiedBy: Actor
        protected set
}
