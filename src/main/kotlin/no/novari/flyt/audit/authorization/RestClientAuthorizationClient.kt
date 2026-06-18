package no.novari.flyt.audit.authorization

import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body
import java.util.UUID

class RestClientAuthorizationClient(
    private val restClient: RestClient,
) : AuthorizationClient {
    override fun findByObjectIdentifier(oid: UUID): AuthorizedUserDto? =
        try {
            restClient
                .get()
                .uri("/{oid}", oid)
                .retrieve()
                .body<AuthorizedUserDto>()
        } catch (_: RestClientResponseException) {
            null
        }

    override fun lookupUsers(oids: List<UUID>): List<AuthorizedUserDto> {
        if (oids.isEmpty()) return emptyList()
        return restClient
            .post()
            .uri("/actions/lookup")
            .body(oids)
            .retrieve()
            .body(object : ParameterizedTypeReference<List<AuthorizedUserDto>>() {})
            ?: emptyList()
    }
}
