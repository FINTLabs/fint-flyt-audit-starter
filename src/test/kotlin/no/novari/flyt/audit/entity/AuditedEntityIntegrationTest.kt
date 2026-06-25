package no.novari.flyt.audit.entity

import no.novari.flyt.audit.actor.Actor
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
class AuditedEntityIntegrationTest {
    @Autowired
    lateinit var repository: AuditedTestEntityRepository

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `createdAt, createdBy, lastModifiedAt og lastModifiedBy settes ved opprettelse`() {
        val oid = UUID.randomUUID()
        setJwtWithOid(oid)

        val saved = repository.saveAndFlush(AuditedTestEntity().apply { value = "initial" })

        assertThat(saved.createdAt).isNotNull()
        assertThat(saved.createdBy).isEqualTo(Actor.User(oid))
        assertThat(saved.lastModifiedAt).isNotNull()
        assertThat(saved.lastModifiedBy).isEqualTo(Actor.User(oid))
    }

    @Test
    fun `lastModifiedBy oppdateres til ny aktør ved endring`() {
        val creator = UUID.randomUUID()
        val updater = UUID.randomUUID()

        setJwtWithOid(creator)
        val saved = repository.saveAndFlush(AuditedTestEntity().apply { value = "initial" })
        val createdAt = saved.createdAt

        setJwtWithOid(updater)
        saved.value = "updated"
        val updated = repository.saveAndFlush(saved)

        assertThat(updated.createdBy).isEqualTo(Actor.User(creator))
        assertThat(updated.createdAt).isEqualTo(createdAt)
        assertThat(updated.lastModifiedBy).isEqualTo(Actor.User(updater))
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
