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
 * Variant C: utvider [CreatedAuditedEntity] med `last_modified_at` (Instant) og
 * `last_modified_by` (JSONB [Actor]) som oppdateres ved hver endring.
 *
 * Konsumenten eier Flyway-DDL og må i tillegg til [CreatedAuditedEntity]-kolonnene inkludere:
 * ```sql
 * last_modified_at  TIMESTAMP WITH TIME ZONE NOT NULL,
 * last_modified_by  JSONB                    NOT NULL
 * ```
 * Feltene er ekskludert fra Envers `_aud`-tabeller via `@NotAudited`.
 */
@MappedSuperclass
abstract class AuditedEntity : CreatedAuditedEntity() {
    @NotAudited
    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    lateinit var lastModifiedAt: Instant
        protected set

    @NotAudited
    @LastModifiedBy
    @Type(JsonType::class)
    @Column(name = "last_modified_by", columnDefinition = "jsonb", nullable = false)
    lateinit var lastModifiedBy: Actor
        protected set
}
