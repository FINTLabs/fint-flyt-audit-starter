package no.novari.flyt.audit.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.metrics.AuditMetricsListener
import org.hibernate.annotations.Type
import org.hibernate.envers.NotAudited
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Variant B: superclass that sets `created_at` (Instant) and `created_by` (JSONB [Actor])
 * through Spring Data JPA auditing.
 *
 * The app owns the Flyway DDL and must add these columns in its migration:
 * ```sql
 * created_at  TIMESTAMP WITH TIME ZONE NULL,
 * created_by  JSONB                    NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb
 * ```
 * `created_at` is nullable, so tables that get auditing later can keep `NULL` for
 * existing rows instead of a made-up timestamp. Spring Data sets the field
 * on the first insert of new rows.
 *
 * `createdBy` is nullable on the object: a new, unsaved object has `null` until
 * JPA auditing sets the field on persist. The column is still `NOT NULL`, so an
 * insert without active auditing fails in the database (fail fast).
 *
 * `@Audited` entities that extend this class do not need the columns in their `_aud` table,
 * since they are excluded from Envers with `@NotAudited`.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class, AuditMetricsListener::class)
abstract class CreatedAuditedEntity {
    @NotAudited
    @CreatedDate
    @Column(name = "created_at", nullable = true, updatable = false)
    var createdAt: Instant? = null
        protected set

    @NotAudited
    @CreatedBy
    @Type(JsonType::class)
    @Column(name = "created_by", columnDefinition = "jsonb", nullable = false, updatable = false)
    var createdBy: Actor? = null
        protected set
}
