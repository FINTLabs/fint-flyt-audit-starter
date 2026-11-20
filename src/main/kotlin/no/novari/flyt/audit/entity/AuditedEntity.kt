package no.novari.flyt.audit.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import no.novari.flyt.audit.actor.Actor
import org.hibernate.annotations.Type
import org.hibernate.envers.NotAudited
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import java.time.Instant

/**
 * Variant C: extends [CreatedAuditedEntity] with `last_modified_at` (Instant) and
 * `last_modified_by` (JSONB [Actor]), updated on every change.
 *
 * The app owns the Flyway DDL and must add, in addition to the [CreatedAuditedEntity] columns:
 * ```sql
 * last_modified_at  TIMESTAMP WITH TIME ZONE NULL,
 * last_modified_by  JSONB                    NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb
 * ```
 * `last_modified_at` is nullable for the same reason as `created_at`, see [CreatedAuditedEntity].
 * The fields are excluded from Envers `_aud` tables with `@NotAudited`.
 */
@MappedSuperclass
abstract class AuditedEntity : CreatedAuditedEntity() {
    @NotAudited
    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = true)
    var lastModifiedAt: Instant? = null
        protected set

    @NotAudited
    @LastModifiedBy
    @Type(JsonType::class)
    @Column(name = "last_modified_by", columnDefinition = "jsonb", nullable = false)
    var lastModifiedBy: Actor? = null
        protected set
}
