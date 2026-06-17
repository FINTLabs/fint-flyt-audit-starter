package no.novari.flyt.audit.history

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.authorization.AuthorizationClient
import no.novari.flyt.audit.authorization.AuthorizedUserDto
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import java.util.UUID

class ActorEnrichmentServiceTest {
    @Test
    fun `tom aktørliste gir tom map og kaller ikke klienten`() {
        val client = RecordingClient()
        val service = ActorEnrichmentService(client)

        val result = service.enrich(emptyList())

        assertThat(result).isEmpty()
        assertThat(client.calls).isEmpty()
    }

    @Test
    fun `aktører uten User gir tom map og kaller ikke klienten`() {
        val client = RecordingClient()
        val service = ActorEnrichmentService(client)

        val result = service.enrich(listOf(Actor.System, Actor.M2M("klient"), Actor.Unknown))

        assertThat(result).isEmpty()
        assertThat(client.calls).isEmpty()
    }

    @Test
    fun `User-aktører hydreres med navn fra klienten`() {
        val oid1 = UUID.randomUUID()
        val oid2 = UUID.randomUUID()
        val client =
            RecordingClient(
                response =
                    listOf(
                        AuthorizedUserDto(oid1, "Ola Nordmann"),
                        AuthorizedUserDto(oid2, "Kari Nordmann"),
                    ),
            )
        val service = ActorEnrichmentService(client)

        val result = service.enrich(listOf(Actor.User(oid1), Actor.User(oid2), Actor.System))

        assertThat(result).containsOnly(
            entry(oid1, "Ola Nordmann"),
            entry(oid2, "Kari Nordmann"),
        )
    }

    @Test
    fun `dupliserte oid-er slås opp kun én gang`() {
        val oid = UUID.randomUUID()
        val client = RecordingClient(response = listOf(AuthorizedUserDto(oid, "Ola Nordmann")))
        val service = ActorEnrichmentService(client)

        service.enrich(listOf(Actor.User(oid), Actor.User(oid), Actor.User(oid)))

        assertThat(client.calls).hasSize(1)
        assertThat(client.calls.single()).containsExactly(oid)
    }

    @Test
    fun `feil fra klienten gir tom map i stedet for å kaste`() {
        val oid = UUID.randomUUID()
        val client = RecordingClient(error = RuntimeException("auth-service nede"))
        val service = ActorEnrichmentService(client)

        val result = service.enrich(listOf(Actor.User(oid)))

        assertThat(result).isEmpty()
    }

    private class RecordingClient(
        private val response: List<AuthorizedUserDto> = emptyList(),
        private val error: Exception? = null,
    ) : AuthorizationClient {
        val calls = mutableListOf<List<UUID>>()

        override fun findByObjectIdentifier(oid: UUID): AuthorizedUserDto? =
            throw UnsupportedOperationException("ikke brukt i disse testene")

        override fun lookupUsers(oids: List<UUID>): List<AuthorizedUserDto> {
            calls += oids
            error?.let { throw it }
            return response
        }
    }
}
