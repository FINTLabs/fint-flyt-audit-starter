package no.novari.flyt.audit.actor

import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Hydrerer visningsnavn for aktører ved presentasjons-tid.
 *
 * Kun [Actor.User] har navn å hente — navnet ligger ikke i endringsloggen,
 * men hentes via [ActorNameLookup] (default: `fint-flyt-authorization-service`
 * over HTTP; kan overstyres per tjeneste — se README).
 * Alle oid-er slås opp i ett batch-kall for å unngå N+1 fra historikk-API.
 *
 * Oppslaget er failsafe: feiler lookup, returneres en tom map slik at
 * historikk-API-et fortsatt svarer (uten navn) i stedet for å feile.
 */
class ActorEnrichmentService(
    private val lookup: ActorNameLookup,
) {
    fun enrich(actors: Collection<Actor>): Map<UUID, String?> {
        val oids =
            actors
                .filterIsInstance<Actor.User>()
                .map { it.oid }
                .distinct()
        if (oids.isEmpty()) return emptyMap()

        return try {
            lookup.lookupNames(oids)
        } catch (ex: Exception) {
            logger.warn("Klarte ikke hydrere {} aktør(er) — returnerer tom map", oids.size, ex)
            emptyMap()
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ActorEnrichmentService::class.java)
    }
}
