package no.novari.flyt.audit.actor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

class ActorHeaderTest {
    @Test
    fun `header-navnet er flyt actor`() {
        assertThat(ActorHeader.HEADER_NAME).isEqualTo("flyt.actor")
    }

    @Test
    fun `User serialiseres til header-verdi med samme kontrakt som JSONB`() {
        val oid = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val value = ActorHeader.toHeaderValue(Actor.User(oid))

        assertThat(String(value, StandardCharsets.UTF_8)).isEqualTo("""{"type":"USER","oid":"$oid"}""")
    }

    @Test
    fun `header-verdi roundtripper alle aktortyper`() {
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
    fun `fromHeaderValueOrNull returnerer null for manglende eller ugyldig header`() {
        assertThat(ActorHeader.fromHeaderValueOrNull(null as ByteArray?)).isNull()
        assertThat(ActorHeader.fromHeaderValueOrNull("ikke-json")).isNull()
    }
}
