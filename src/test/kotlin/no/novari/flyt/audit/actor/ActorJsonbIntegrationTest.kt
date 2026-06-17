package no.novari.flyt.audit.actor

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ActorJsonbIntegrationTest {
    @Autowired
    lateinit var repository: ActorHolderRepository

    @Autowired
    lateinit var em: EntityManager

    private val mapper = ObjectMapper()

    @Test
    fun `User-aktør lagres og leses som JSONB`() {
        val oid = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val saved = repository.saveAndFlush(ActorHolderEntity(actor = Actor.User(oid)))
        em.clear()

        val reloaded = repository.findById(saved.id!!).orElseThrow()
        assertThat(reloaded.actor).isEqualTo(Actor.User(oid))

        assertActorJson(saved.id!!, """{"type":"USER","oid":"$oid"}""")
    }

    @Test
    fun `System-aktør lagres og leses som JSONB`() {
        val saved = repository.saveAndFlush(ActorHolderEntity(actor = Actor.System))
        em.clear()

        val reloaded = repository.findById(saved.id!!).orElseThrow()
        assertThat(reloaded.actor).isEqualTo(Actor.System)

        assertActorJson(saved.id!!, """{"type":"SYSTEM"}""")
    }

    @Test
    fun `M2M-aktør lagres og leses som JSONB`() {
        val saved = repository.saveAndFlush(ActorHolderEntity(actor = Actor.M2M("client-abc")))
        em.clear()

        val reloaded = repository.findById(saved.id!!).orElseThrow()
        assertThat(reloaded.actor).isEqualTo(Actor.M2M("client-abc"))

        assertActorJson(saved.id!!, """{"type":"M2M","clientId":"client-abc"}""")
    }

    @Test
    fun `Unknown-aktør lagres og leses som JSONB`() {
        val saved = repository.saveAndFlush(ActorHolderEntity(actor = Actor.Unknown))
        em.clear()

        val reloaded = repository.findById(saved.id!!).orElseThrow()
        assertThat(reloaded.actor).isEqualTo(Actor.Unknown)

        assertActorJson(saved.id!!, """{"type":"UNKNOWN"}""")
    }

    private fun assertActorJson(
        id: Long,
        expectedJson: String,
    ) {
        val actualJson =
            em
                .createNativeQuery("select actor::text from actor_holder where id = :id")
                .setParameter("id", id)
                .singleResult as String

        // Postgres jsonb normaliserer key-rekkefølge; sammenlign strukturelt.
        assertThat(mapper.readTree(actualJson)).isEqualTo(mapper.readTree(expectedJson))
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
