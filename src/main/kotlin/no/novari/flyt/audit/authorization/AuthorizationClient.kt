package no.novari.flyt.audit.authorization

import java.util.UUID

interface AuthorizationClient {
    fun findByObjectIdentifier(oid: UUID): AuthorizedUserDto?

    fun lookupUsers(oids: List<UUID>): List<AuthorizedUserDto>
}
