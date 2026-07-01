package no.novari.flyt.audit.actor

import no.novari.flyt.audit.authorization.AuthorizationClient
import java.util.UUID

/**
 * Strategi for å hente visningsnavn for en samling `Actor.User`-oid-er.
 *
 * Skiller "hvordan hentes navnet" fra "hvordan brukes det". Default-implementasjonen
 * ([HttpActorNameLookup]) kaller `fint-flyt-authorization-service` over HTTP.
 * `fint-flyt-authorization-service` selv registrerer en egen bean som slår opp
 * lokalt i sin egen `UserRepository` — se README-seksjon "Lokal hydrering".
 *
 * Returverdien inneholder én entry per oid; ukjent oid representeres som `null`-verdi.
 * Implementasjoner kan kaste ved feil — kallere ([ActorEnrichmentService],
 * [ActorDisplayResolver]) håndterer det failsafe.
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
