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
 * Test-entitet som verifiserer at [Actor] mapper riktig mot en JSONB-kolonne.
 * Kun for tester — ikke en del av starterens main-kode.
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
