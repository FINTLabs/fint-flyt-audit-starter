package no.novari.flyt.audit.actor

import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Mapper [Actor] → visningsnavn for REST-DTOer.
 *
 * Brukes fra tjenestens mapping-lag når `createdBy`/`lastModifiedBy` skal
 * eksponeres som streng mot web. For listeresponser bør [resolveAll] brukes
 * for å unngå N+1-oppslag mot [ActorNameLookup].
 *
 * Fallback-verdier for `System`, `Unknown`, `M2M` og "bruker ikke funnet"
 * styres av [ActorDisplayProperties] — kan overstyres per tjeneste.
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
            logger.warn("Klarte ikke hydrere {} aktør-navn — bruker fallback", oids.size, ex)
            emptyMap()
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ActorDisplayResolver::class.java)
    }
}
