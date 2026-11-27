package no.novari.flyt.audit.authorization

import java.util.UUID

/**
 * Client that looks up user names in authorization-service by OID.
 *
 * Used by [no.novari.flyt.audit.actor.HttpActorNameLookup] to resolve display names.
 * The default implementation calls `POST /api/intern-klient/authorization/users/actions/lookup`.
 * Can be wrapped in [CachingAuthorizationClient] to reduce calls to authorization-service.
 */
interface AuthorizationClient {
    fun findByObjectIdentifier(oid: UUID): AuthorizedUserDto?

    fun lookupUsers(oids: List<UUID>): List<AuthorizedUserDto>
}
