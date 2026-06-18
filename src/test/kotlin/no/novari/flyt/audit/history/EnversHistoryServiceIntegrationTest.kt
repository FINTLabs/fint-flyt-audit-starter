package no.novari.flyt.audit.history

import jakarta.persistence.EntityManager
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.authorization.AuthorizationClient
import no.novari.flyt.audit.authorization.AuthorizedUserDto
import no.novari.flyt.audit.config.ApplicationContextHolder
import no.novari.flyt.audit.entity.AuditTestConfig
import no.novari.flyt.audit.revision.RevisedTestEntity
import no.novari.flyt.audit.revision.RevisedTestEntityRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
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
class EnversHistoryServiceIntegrationTest {
    @Autowired
    lateinit var entityRepository: RevisedTestEntityRepository

    @Autowired
    lateinit var entityManager: EntityManager

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate: TransactionTemplate by lazy { TransactionTemplate(transactionManager) }

    private val userOid = UUID.randomUUID()
    private val fakeClient =
        object : AuthorizationClient {
            override fun findByObjectIdentifier(oid: UUID) = null

            override fun lookupUsers(oids: List<UUID>) = oids.map { AuthorizedUserDto(it, "Ola Nordmann") }
        }

    private lateinit var historyService: EnversHistoryService<RevisedTestEntity, Long>

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM revised_test_entity_aud")
        jdbcTemplate.execute("DELETE FROM revinfo")
        jdbcTemplate.execute("DELETE FROM revised_test_entity")

        historyService =
            object : EnversHistoryService<RevisedTestEntity, Long>(
                RevisedTestEntity::class.java,
                entityManager,
                ActorEnrichmentService(fakeClient),
            ) {}
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `historikk returneres nyeste først med riktig type og hydrert navn`() {
        setJwtWithOid(userOid)
        val saved = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })
        val id = saved.id!!

        saved.name = "v2"
        entityRepository.saveAndFlush(saved)

        val page = findHistory(id, PageRequest.of(0, 20))

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content.map { it.type })
            .containsExactly(HistoryEventType.UPDATED, HistoryEventType.CREATED)
        assertThat(page.content.map { it.snapshot?.name })
            .containsExactly("v2", "v1")
        assertThat(page.content).allSatisfy {
            assertThat(it.actor).isEqualTo(Actor.User(userOid))
            assertThat(it.actorDisplay).isEqualTo("Ola Nordmann")
        }
    }

    @Test
    fun `sletting gir DELETED-entry med null snapshot`() {
        setJwtWithOid(userOid)
        val saved = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })
        val id = saved.id!!

        entityRepository.delete(saved)
        entityRepository.flush()

        val page = findHistory(id, PageRequest.of(0, 20))

        assertThat(page.content.first().type).isEqualTo(HistoryEventType.DELETED)
        assertThat(page.content.first().snapshot).isNull()
    }

    @Test
    fun `System-aktør gir ingen actorDisplay`() {
        SecurityContextHolder.clearContext()
        val saved = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })
        val id = saved.id!!

        val page = findHistory(id, PageRequest.of(0, 20))

        assertThat(page.content.single().actor).isEqualTo(Actor.System)
        assertThat(page.content.single().actorDisplay).isNull()
    }

    @Test
    fun `paginering avgrenser innhold men teller totalt`() {
        setJwtWithOid(userOid)
        val saved = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })
        val id = saved.id!!
        repeat(3) { i ->
            saved.name = "v${i + 2}"
            entityRepository.saveAndFlush(saved)
        }

        val page = findHistory(id, PageRequest.of(0, 2))

        assertThat(page.totalElements).isEqualTo(4)
        assertThat(page.content).hasSize(2)
        assertThat(page.content.map { it.snapshot?.name }).containsExactly("v4", "v3")
    }

    @Test
    fun `from-filter ekskluderer eldre revisjoner`() {
        setJwtWithOid(userOid)
        val saved = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })
        val id = saved.id!!

        val future = Instant.now().plusSeconds(3600)
        val page = findHistory(id, PageRequest.of(0, 20), HistoryFilter(from = future))

        assertThat(page.totalElements).isZero()
        assertThat(page.content).isEmpty()
    }

    private fun findHistory(
        id: Long,
        pageable: PageRequest,
        filter: HistoryFilter = HistoryFilter(),
    ) = transactionTemplate.execute {
        historyService.findHistory(id, pageable, filter)
    }!!

    private fun setJwtWithOid(oid: UUID) {
        val jwt =
            Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                mapOf("alg" to "RS256"),
                mapOf("oid" to oid.toString()),
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
