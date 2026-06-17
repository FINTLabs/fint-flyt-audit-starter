package no.novari.flyt.audit.history

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.authorization.AuthorizationClient
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Hydrerer visningsnavn for aktører ved presentasjons-tid.
 *
 * Kun [Actor.User] har navn å hente — navnet ligger ikke i endringsloggen,
 * men hentes fra `fint-flyt-authorization-service` (se løsningsanalysen §3.6.5).
 * Alle oid-er slås opp i ett batch-kall for å unngå N+1 fra historikk-API.
 *
 * Oppslaget er failsafe: feiler auth-service, returneres en tom map slik at
 * historikk-API-et fortsatt svarer (uten navn) i stedet for å feile.
 */
class ActorEnrichmentService(
    private val client: AuthorizationClient,
) {
    fun enrich(actors: Collection<Actor>): Map<UUID, String?> {
        val oids =
            actors
                .filterIsInstance<Actor.User>()
                .map { it.oid }
                .distinct()
        if (oids.isEmpty()) return emptyMap()

        return try {
            client
                .lookupUsers(oids)
                .associate { it.objectIdentifier to it.name }
        } catch (ex: Exception) {
            logger.warn("Klarte ikke hydrere {} aktør(er) fra auth-service", oids.size, ex)
            emptyMap()
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ActorEnrichmentService::class.java)
    }
}
