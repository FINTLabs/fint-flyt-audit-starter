package no.novari.flyt.audit.actor

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Maps [Actor] to a display name for REST DTOs.
 *
 * Used from the service's mapping layer when `createdBy`/`lastModifiedBy` is
 * exposed as a string. Use [resolveAll] for lists,
 * to avoid N+1 lookups in [ActorNameLookup].
 *
 * The fallback values for `System`, `Unknown`, `M2M` and "user not found"
 * come from [ActorDisplayProperties] and can be overridden per service.
 */
class ActorDisplayResolver(
    private val lookup: ActorNameLookup,
    private val properties: ActorDisplayProperties,
) {
    fun resolveAll(actors: Collection<Actor?>): Map<Actor, String?> {
        val users = actors.filterIsInstance<Actor.User>().distinct()
        val names = safeLookup(users.map { it.oid })
        return actors
            .asSequence()
            .filterNotNull()
            .distinct()
            .associateWith { display(it, names) }
    }

    fun resolve(actor: Actor?): String? {
        if (actor == null) return null
        val names =
            if (actor is Actor.User) safeLookup(listOf(actor.oid)) else emptyMap()
        return display(actor, names)
    }

    private fun display(
        actor: Actor,
        names: Map<UUID, String?>,
    ): String? =
        when (actor) {
            is Actor.User -> names[actor.oid] ?: properties.unknownUser
            is Actor.M2M -> properties.m2m ?: actor.clientId
            Actor.System -> properties.system
            Actor.Unknown -> properties.unknown
        }

    private fun safeLookup(oids: List<UUID>): Map<UUID, String?> {
        if (oids.isEmpty()) return emptyMap()
        return try {
            lookup.lookupNames(oids)
        } catch (ex: Exception) {
            logger.warn("Could not resolve {} actor names, using the fallback", oids.size, ex)
            emptyMap()
        }
    }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(ActorDisplayResolver::class.java)
    }
}
