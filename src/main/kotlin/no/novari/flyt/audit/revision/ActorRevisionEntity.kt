package no.novari.flyt.audit.revision

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import no.novari.flyt.audit.actor.Actor
import org.hibernate.annotations.Type
import org.hibernate.envers.RevisionEntity
import org.hibernate.envers.RevisionNumber
import org.hibernate.envers.RevisionTimestamp

/**
 * Envers-revisjonsentitet lagret i `revinfo`-tabellen for hver transaksjon som endrer auditerte entiteter.
 *
 * Konsumenten eier Flyway-DDL for `revinfo` — se malen `flyt-audit-templates/V1__revinfo.sql`.
 * Feltet [actor] inneholder aktøren som utløste endringen (JSONB) og populeres av [ActorRevisionListener].
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(ActorRevisionListener::class)
class ActorRevisionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_seq")
    @SequenceGenerator(name = "revinfo_seq", sequenceName = "revinfo_seq", allocationSize = 50)
    @RevisionNumber
    var rev: Long = 0

    @RevisionTimestamp
    var revtstmp: Long = 0

    @Type(JsonType::class)
    @Column(columnDefinition = "jsonb", nullable = false)
    var actor: Actor = Actor.Unknown
}
