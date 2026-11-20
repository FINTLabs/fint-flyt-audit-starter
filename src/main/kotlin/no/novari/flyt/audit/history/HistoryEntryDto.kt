package no.novari.flyt.audit.history

import no.novari.flyt.audit.actor.Actor
import java.time.Instant

/**
 * One row in the change history of an entity.
 *
 * @param timestamp when the revision was recorded
 * @param type the kind of change the revision represents
 * @param actor the actor that made the change, as stored
 * @param actorDisplay display name for the actor, resolved by `ActorDisplayResolver` at
 *   presentation time: the user name from `fint-flyt-authorization-service`, or the
 *   fallback value for System/M2M/Unknown (e.g. `"System"`). `null` when the name could not
 *   be looked up, or when the fallback value is configured as `null`.
 * @param snapshot the state of the revision as `mapSnapshot` exposes it. `null` for
 *   deleted revisions (DELETED).
 */
data class HistoryEntryDto<T>(
    val timestamp: Instant,
    val type: HistoryEventType,
    val actor: Actor,
    val actorDisplay: String?,
    val snapshot: T?,
)
