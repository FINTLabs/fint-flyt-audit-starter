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
 * Variant B: superklasse som populerer `created_at` (Instant) og `created_by` (JSONB [Actor])
 * automatisk via Spring Data JPA Auditing.
 *
 * Konsumenten eier Flyway-DDL og må inkludere disse kolonnene i sin migrering:
 * ```sql
 * created_at  TIMESTAMP WITH TIME ZONE NULL,
 * created_by  JSONB                    NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb
 * ```
 * `created_at` er nullable slik at retrofit av eksisterende tabeller kan bruke `NULL` for
 * migrerte rader i stedet for et forfalsket tidsstempel. Spring Data setter feltet automatisk
 * ved første insert på nye rader.
 *
 * `@Audited`-entiteter som arver denne klassen trenger ikke speile kolonnene i `_aud`-tabellen
 * — de er ekskludert fra Envers via `@NotAudited`.
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
    lateinit var createdBy: Actor
        protected set
}
