package no.novari.flyt.audit.actor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

class ActorJacksonTest {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    @Test
    fun `User is serialized with type and oid`() {
        val oid = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val json = mapper.writeValueAsString(Actor.User(oid) as Actor)
        assertThat(json).isEqualTo("""{"type":"USER","oid":"$oid"}""")
    }

    @Test
    fun `System is serialized as type only`() {
        val json = mapper.writeValueAsString(Actor.System as Actor)
        assertThat(json).isEqualTo("""{"type":"SYSTEM"}""")
    }

    @Test
    fun `M2M is serialized with type and clientId`() {
        val json = mapper.writeValueAsString(Actor.M2M("00000000-0000-0000-0000-000000000001") as Actor)
        assertThat(json).isEqualTo("""{"type":"M2M","clientId":"00000000-0000-0000-0000-000000000001"}""")
    }

    @Test
    fun `Unknown is serialized as type only`() {
        val json = mapper.writeValueAsString(Actor.Unknown as Actor)
        assertThat(json).isEqualTo("""{"type":"UNKNOWN"}""")
    }

    @Test
    fun `User round trip keeps the oid`() {
        val original = Actor.User(UUID.randomUUID()) as Actor
        val json = mapper.writeValueAsString(original)
        val parsed = mapper.readValue(json, Actor::class.java)
        assertThat(parsed).isEqualTo(original)
    }

    @Test
    fun `System round trip gives the same instance`() {
        val json = mapper.writeValueAsString(Actor.System as Actor)
        val parsed = mapper.readValue(json, Actor::class.java)
        assertThat(parsed).isEqualTo(Actor.System)
    }

    @Test
    fun `M2M round trip keeps the clientId`() {
        val original = Actor.M2M("client-abc") as Actor
        val json = mapper.writeValueAsString(original)
        val parsed = mapper.readValue(json, Actor::class.java)
        assertThat(parsed).isEqualTo(original)
    }

    @Test
    fun `Unknown round trip gives the same instance`() {
        val json = mapper.writeValueAsString(Actor.Unknown as Actor)
        val parsed = mapper.readValue(json, Actor::class.java)
        assertThat(parsed).isEqualTo(Actor.Unknown)
    }

    @Test
    fun `type property is not in the JSON output (only the discriminator)`() {
        val json = mapper.writeValueAsString(Actor.User(UUID.randomUUID()) as Actor)
        val typeOccurrences = json.split("\"type\":").size - 1
        assertThat(typeOccurrences).isEqualTo(1)
    }
}
