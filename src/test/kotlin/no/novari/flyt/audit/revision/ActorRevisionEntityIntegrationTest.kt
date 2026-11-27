package no.novari.flyt.audit.revision

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorContext
import no.novari.flyt.audit.entity.AuditTestConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(AuditTestConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ActorRevisionEntityIntegrationTest {
    @Autowired
    lateinit var entityRepository: RevisedTestEntityRepository

    @Autowired
    lateinit var revisionRepository: ActorRevisionEntityRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

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
    fun `actor is User for a revision with a JWT`() {
        val oid = UUID.randomUUID()
        setJwtWithOid(oid)

        entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "initial" })

        val revisions = revisionRepository.findAll()
        assertThat(revisions).hasSize(1)
        assertThat(revisions[0].actor).isEqualTo(Actor.User(oid))
    }

    @Test
    fun `actor is System without a security context`() {
        SecurityContextHolder.clearContext()

        entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "test" })

        val revisions = revisionRepository.findAll()
        assertThat(revisions).hasSize(1)
        assertThat(revisions[0].actor).isEqualTo(Actor.System)
    }

    @Test
    fun `an update creates a new revision with the right actor`() {
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

    @Test
    fun `withActor around the whole transaction records the actor in the revision`() {
        val actor = Actor.User(UUID.randomUUID())

        ActorContext.withActor(actor) {
            TransactionTemplate(transactionManager).executeWithoutResult {
                entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "test" })
            }
        }

        assertThat(revisionRepository.findAll().single().actor).isEqualTo(actor)
    }

    @Test
    fun `withActor inside a transaction does not reach the revision, which Envers creates at commit`() {
        val actor = Actor.User(UUID.randomUUID())

        TransactionTemplate(transactionManager).executeWithoutResult {
            ActorContext.withActor(actor) {
                entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "test" })
            }
        }

        assertThat(revisionRepository.findAll().single().actor).isEqualTo(Actor.System)
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
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine")
    }
}
