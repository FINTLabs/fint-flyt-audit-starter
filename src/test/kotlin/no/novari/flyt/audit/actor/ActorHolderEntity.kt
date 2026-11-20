package no.novari.flyt.audit.actor

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type

/**
 * Test entity that checks that [Actor] maps to a JSONB column.
 * Only for tests, not part of the starter's main code.
 */
@Entity
@Table(name = "actor_holder")
class ActorHolderEntity(
    @Type(JsonType::class)
    @Column(name = "actor", columnDefinition = "jsonb", nullable = false)
    var actor: Actor = Actor.Unknown,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
