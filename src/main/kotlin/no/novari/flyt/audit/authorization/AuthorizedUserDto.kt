package no.novari.flyt.audit.authorization

import java.util.UUID

data class AuthorizedUserDto(
    val objectIdentifier: UUID,
    val name: String?,
)
