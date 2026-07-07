package no.novari.flyt.audit.authorization

import java.util.UUID

/**
 * Klient for å slå opp brukernavn fra auth-service basert på OID.
 *
 * Brukes via [no.novari.flyt.audit.actor.HttpActorNameLookup] for å hydrere visningsnavn.
 * Standardimplementasjonen kaller `POST /api/intern-klient/authorization/users/actions/lookup`.
 * Kan pakkes inn med [CachingAuthorizationClient] for å redusere antall kall mot auth-service.
 */
interface AuthorizationClient {
    fun findByObjectIdentifier(oid: UUID): AuthorizedUserDto?

    fun lookupUsers(oids: List<UUID>): List<AuthorizedUserDto>
}
