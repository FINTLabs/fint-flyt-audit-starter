package no.novari.flyt.audit.actor

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import java.util.UUID

class ActorEnrichmentServiceTest {
    @Test
    fun `tom aktørliste gir tom map og kaller ikke lookup`() {
        val lookup = RecordingLookup()
        val service = ActorEnrichmentService(lookup)

        val result = service.enrich(emptyList())

        assertThat(result).isEmpty()
        assertThat(lookup.calls).isEmpty()
    }

    @Test
    fun `aktører uten User gir tom map og kaller ikke lookup`() {
        val lookup = RecordingLookup()
        val service = ActorEnrichmentService(lookup)

        val result = service.enrich(listOf(Actor.System, Actor.M2M("klient"), Actor.Unknown))

        assertThat(result).isEmpty()
        assertThat(lookup.calls).isEmpty()
    }

    @Test
    fun `User-aktører hydreres med navn fra lookup`() {
        val oid1 = UUID.randomUUID()
        val oid2 = UUID.randomUUID()
        val lookup =
            RecordingLookup(response = mapOf(oid1 to "Ola Nordmann", oid2 to "Kari Nordmann"))
        val service = ActorEnrichmentService(lookup)

        val result = service.enrich(listOf(Actor.User(oid1), Actor.User(oid2), Actor.System))

        assertThat(result).containsOnly(
            entry(oid1, "Ola Nordmann"),
            entry(oid2, "Kari Nordmann"),
        )
    }

    @Test
    fun `dupliserte oid-er slås opp kun én gang`() {
        val oid = UUID.randomUUID()
        val lookup = RecordingLookup(response = mapOf(oid to "Ola Nordmann"))
        val service = ActorEnrichmentService(lookup)

        service.enrich(listOf(Actor.User(oid), Actor.User(oid), Actor.User(oid)))

        assertThat(lookup.calls).hasSize(1)
        assertThat(lookup.calls.single()).containsExactly(oid)
    }

    @Test
    fun `feil fra lookup gir tom map i stedet for å kaste`() {
        val oid = UUID.randomUUID()
        val lookup = RecordingLookup(error = RuntimeException("nede"))
        val service = ActorEnrichmentService(lookup)

        val result = service.enrich(listOf(Actor.User(oid)))

        assertThat(result).isEmpty()
    }

    private class RecordingLookup(
        private val response: Map<UUID, String?> = emptyMap(),
        private val error: Exception? = null,
    ) : ActorNameLookup {
        val calls = mutableListOf<Collection<UUID>>()

        override fun lookupNames(oids: Collection<UUID>): Map<UUID, String?> {
            calls += oids
            error?.let { throw it }
            return response
        }
    }
}
