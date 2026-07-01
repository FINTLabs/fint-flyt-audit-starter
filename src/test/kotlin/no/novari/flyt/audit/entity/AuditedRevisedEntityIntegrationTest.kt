package no.novari.flyt.audit.entity

import jakarta.persistence.EntityManager
import no.novari.flyt.audit.actor.ActorEnrichmentService
import no.novari.flyt.audit.actor.HttpActorNameLookup
import no.novari.flyt.audit.authorization.AuthorizationClient
import no.novari.flyt.audit.authorization.AuthorizedUserDto
import no.novari.flyt.audit.config.ApplicationContextHolder
import no.novari.flyt.audit.history.EnversHistoryService
import no.novari.flyt.audit.history.HistoryEventType
import no.novari.flyt.audit.history.HistoryFilter
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

/**
 * Verifies that an entity combining @Audited with AuditedEntity (Variant D/E) works correctly:
 * the four inherited audit columns (created_at, created_by, last_modified_at, last_modified_by)
 * are excluded from the _aud table via @NotAudited, so consumers do not need to replicate them
 * in their Flyway migration for the _aud table.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(AuditTestConfig::class, ApplicationContextHolder::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditedRevisedEntityIntegrationTest {
    @Autowired
    lateinit var entityRepository: AuditedRevisedTestEntityRepository

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

            override fun lookupUsers(oids: List<UUID>) = oids.map { AuthorizedUserDto(it, "Test Bruker") }
        }

    private lateinit var historyService: EnversHistoryService<AuditedRevisedTestEntity, Long>

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM audited_revised_test_entity_aud")
        jdbcTemplate.execute("DELETE FROM revinfo")
        jdbcTemplate.execute("DELETE FROM audited_revised_test_entity")

        historyService =
            object : EnversHistoryService<AuditedRevisedTestEntity, Long>(
                AuditedRevisedTestEntity::class.java,
                entityManager,
                ActorEnrichmentService(HttpActorNameLookup(fakeClient)),
            ) {}
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `_aud-tabell inneholder ikke audit-kolonner (created_at, created_by, last_modified_at, last_modified_by)`() {
        setJwtWithOid(userOid)
        entityRepository.saveAndFlush(AuditedRevisedTestEntity().apply { name = "v1" })

        val columns =
            jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'audited_revised_test_entity_aud'",
                String::class.java,
            )

        assertThat(columns).containsExactlyInAnyOrder("id", "rev", "revtype", "name")
        assertThat(columns).doesNotContain("created_at", "created_by", "last_modified_at", "last_modified_by")
    }

    @Test
    fun `historikk returneres korrekt for entitet som kombinerer @Audited og AuditedEntity`() {
        setJwtWithOid(userOid)
        val saved = entityRepository.saveAndFlush(AuditedRevisedTestEntity().apply { name = "v1" })
        val id = saved.id!!

        saved.name = "v2"
        entityRepository.saveAndFlush(saved)

        val page = findHistory(id, PageRequest.of(0, 20))

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content.map { it.type })
            .containsExactly(HistoryEventType.UPDATED, HistoryEventType.CREATED)
        assertThat(page.content.map { it.snapshot?.name }).containsExactly("v2", "v1")
    }

    @Test
    fun `audit-kolonner på entity-tabellen er fortsatt populert`() {
        setJwtWithOid(userOid)
        val saved = entityRepository.saveAndFlush(AuditedRevisedTestEntity().apply { name = "v1" })

        assertThat(saved.createdAt).isNotNull()
        assertThat(saved.createdBy).isNotNull()
        assertThat(saved.lastModifiedAt).isNotNull()
        assertThat(saved.lastModifiedBy).isNotNull()
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
