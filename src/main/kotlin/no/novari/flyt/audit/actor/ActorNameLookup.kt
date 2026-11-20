package no.novari.flyt.audit.actor

import no.novari.flyt.audit.authorization.AuthorizationClient
import java.util.UUID

/**
 * Strategy for looking up display names for a set of `Actor.User` OIDs.
 *
 * Separates how the name is looked up from how it is used. The default implementation
 * ([HttpActorNameLookup]) calls `fint-flyt-authorization-service` over HTTP.
 * `fint-flyt-authorization-service` itself registers its own bean that looks up
 * in its own `UserRepository`, see the README section "Local name resolution".
 *
 * The result has one entry per OID, and an unknown OID has a `null` value.
 * Implementations may throw, and the caller [ActorDisplayResolver] handles it.
 */
fun interface ActorNameLookup {
    fun lookupNames(oids: Collection<UUID>): Map<UUID, String?>
}

class HttpActorNameLookup(
    private val client: AuthorizationClient,
) : ActorNameLookup {
    override fun lookupNames(oids: Collection<UUID>): Map<UUID, String?> {
        if (oids.isEmpty()) return emptyMap()
        return client
            .lookupUsers(oids.toList())
            .associate { it.objectIdentifier to it.name }
    }
}

/**
 * Fallback for apps that neither have an OAuth2 client for `fint-flyt-authorization-service`
 * configured, nor register their own [ActorNameLookup] (as `fint-flyt-authorization-service`
 * itself does). Never looks up names, so [ActorDisplayResolver] falls back to
 * the `unknownUser`/`system`/`unknown` values from [ActorDisplayProperties] for every actor.
 *
 * Exists so that [ActorDisplayResolver] is always available as a bean, also in
 * services that only use variant B/C without name resolution, see the README section
 * "Without name resolution".
 */
class NoOpActorNameLookup : ActorNameLookup {
    override fun lookupNames(oids: Collection<UUID>): Map<UUID, String?> = emptyMap()
}
