package no.novari.flyt.audit.actor

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ActorJacksonTest {
    private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `User serialiseres med type og oid`() {
        val oid = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val json = mapper.writeValueAsString(Actor.User(oid) as Actor)
        assertThat(json).isEqualTo("""{"type":"USER","oid":"$oid"}""")
    }

    @Test
    fun `System serialiseres som kun type`() {
        val json = mapper.writeValueAsString(Actor.System as Actor)
        assertThat(json).isEqualTo("""{"type":"SYSTEM"}""")
    }

    @Test
    fun `M2M serialiseres med type og clientId`() {
        val json = mapper.writeValueAsString(Actor.M2M("00000000-0000-0000-0000-000000000001") as Actor)
        assertThat(json).isEqualTo("""{"type":"M2M","clientId":"00000000-0000-0000-0000-000000000001"}""")
    }

    @Test
    fun `Unknown serialiseres som kun type`() {
        val json = mapper.writeValueAsString(Actor.Unknown as Actor)
        assertThat(json).isEqualTo("""{"type":"UNKNOWN"}""")
    }

    @Test
    fun `User round-trip bevarer oid`() {
        val original = Actor.User(UUID.randomUUID()) as Actor
        val json = mapper.writeValueAsString(original)
        val parsed = mapper.readValue(json, Actor::class.java)
        assertThat(parsed).isEqualTo(original)
    }

    @Test
    fun `System round-trip gir lik instans`() {
        val json = mapper.writeValueAsString(Actor.System as Actor)
        val parsed = mapper.readValue(json, Actor::class.java)
        assertThat(parsed).isEqualTo(Actor.System)
    }

    @Test
    fun `M2M round-trip bevarer clientId`() {
        val original = Actor.M2M("client-abc") as Actor
        val json = mapper.writeValueAsString(original)
        val parsed = mapper.readValue(json, Actor::class.java)
        assertThat(parsed).isEqualTo(original)
    }

    @Test
    fun `Unknown round-trip gir lik instans`() {
        val json = mapper.writeValueAsString(Actor.Unknown as Actor)
        val parsed = mapper.readValue(json, Actor::class.java)
        assertThat(parsed).isEqualTo(Actor.Unknown)
    }

    @Test
    fun `type-property er ikke med i JSON-output (kun via diskriminator)`() {
        val json = mapper.writeValueAsString(Actor.User(UUID.randomUUID()) as Actor)
        val typeOccurrences = json.split("\"type\":").size - 1
        assertThat(typeOccurrences).isEqualTo(1)
    }
}
