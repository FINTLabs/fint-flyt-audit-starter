package no.novari.flyt.audit.entity

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(AuditTestConfig::class)
class CreatedAuditedEntityIntegrationTest {
    @Autowired
    lateinit var repository: CreatedTestEntityRepository

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
        ActorContext.clear()
    }

    @Test
    fun `createdAt og createdBy settes ved lagring`() {
        val oid = UUID.randomUUID()
        setJwtWithOid(oid)

        val saved = repository.saveAndFlush(CreatedTestEntity())

        assertThat(saved.createdAt).isNotNull()
        assertThat(saved.createdBy).isEqualTo(Actor.User(oid))
    }

    @Test
    fun `createdBy er Actor-System uten sikkerhetskontekst`() {
        SecurityContextHolder.clearContext()

        val saved = repository.saveAndFlush(CreatedTestEntity())

        assertThat(saved.createdBy).isEqualTo(Actor.System)
    }

    @Test
    fun `createdBy settes fra scoped aktør uten sikkerhetskontekst`() {
        val actor = Actor.User(UUID.randomUUID())
        SecurityContextHolder.clearContext()

        val saved =
            ActorContext.withActor(actor) {
                repository.saveAndFlush(CreatedTestEntity())
            }

        assertThat(saved.createdBy).isEqualTo(actor)
        assertThat(ActorContext.currentActor()).isNull()
    }

    private fun setJwtWithOid(oid: UUID) {
        val jwt =
            Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                mapOf("alg" to "RS256"),
                mapOf("objectidentifier" to oid.toString()),
            )
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken(jwt, null)
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
    }
}
