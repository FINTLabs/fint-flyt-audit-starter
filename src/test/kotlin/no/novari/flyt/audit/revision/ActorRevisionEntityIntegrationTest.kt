package no.novari.flyt.audit.revision

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.config.ApplicationContextHolder
import no.novari.flyt.audit.entity.AuditTestConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(AuditTestConfig::class, ApplicationContextHolder::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ActorRevisionEntityIntegrationTest {
    @Autowired
    lateinit var entityRepository: RevisedTestEntityRepository

    @Autowired
    lateinit var revisionRepository: ActorRevisionEntityRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM revised_test_entity_aud")
        jdbcTemplate.execute("DELETE FROM revinfo")
        jdbcTemplate.execute("DELETE FROM revised_test_entity")
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `actor settes til User ved revisjon med JWT`() {
        val oid = UUID.randomUUID()
        setJwtWithOid(oid)

        entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "initial" })

        val revisions = revisionRepository.findAll()
        assertThat(revisions).hasSize(1)
        assertThat(revisions[0].actor).isEqualTo(Actor.User(oid))
    }

    @Test
    fun `actor settes til System uten sikkerhetskontekst`() {
        SecurityContextHolder.clearContext()

        entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "test" })

        val revisions = revisionRepository.findAll()
        assertThat(revisions).hasSize(1)
        assertThat(revisions[0].actor).isEqualTo(Actor.System)
    }

    @Test
    fun `ny revisjon opprettes ved oppdatering med korrekt actor`() {
        val oid1 = UUID.randomUUID()
        val oid2 = UUID.randomUUID()

        setJwtWithOid(oid1)
        val saved = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })

        setJwtWithOid(oid2)
        saved.name = "v2"
        entityRepository.saveAndFlush(saved)

        val revisions = revisionRepository.findAll().sortedBy { it.rev }
        assertThat(revisions).hasSize(2)
        assertThat(revisions[0].actor).isEqualTo(Actor.User(oid1))
        assertThat(revisions[1].actor).isEqualTo(Actor.User(oid2))
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
