package no.novari.flyt.audit.actor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

class ActorHeaderTest {
    @Test
    fun `the header name is flyt-actor`() {
        assertThat(ActorHeader.HEADER_NAME).isEqualTo("flyt.actor")
    }

    @Test
    fun `User is serialized to a header value with the same contract as JSONB`() {
        val oid = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val value = ActorHeader.toHeaderValue(Actor.User(oid))

        assertThat(String(value, StandardCharsets.UTF_8)).isEqualTo("""{"type":"USER","oid":"$oid"}""")
    }

    @Test
    fun `header value round trips all actor types`() {
        val actors =
            listOf(
                Actor.User(UUID.randomUUID()),
                Actor.System,
                Actor.M2M("client-abc"),
                Actor.Unknown,
            )

        actors.forEach { actor ->
            assertThat(ActorHeader.fromHeaderValue(ActorHeader.toHeaderValue(actor))).isEqualTo(actor)
        }
    }

    @Test
    fun `fromHeaderValueOrNull returns null for a missing or invalid header`() {
        assertThat(ActorHeader.fromHeaderValueOrNull(null as ByteArray?)).isNull()
        assertThat(ActorHeader.fromHeaderValueOrNull("ikke-json")).isNull()
    }
}
