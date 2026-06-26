package no.novari.flyt.audit.history

import no.novari.flyt.audit.actor.Actor
import java.time.Instant

data class EntityHistoryEntryDto<T, ID>(
    val entityId: ID,
    val timestamp: Instant,
    val type: HistoryEventType,
    val actor: Actor,
    val actorDisplay: String?,
    val snapshot: T?,
)
