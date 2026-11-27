package no.novari.flyt.audit.history

import jakarta.persistence.EntityManager
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorDisplayProperties
import no.novari.flyt.audit.actor.ActorDisplayResolver
import no.novari.flyt.audit.actor.HttpActorNameLookup
import no.novari.flyt.audit.authorization.AuthorizationClient
import no.novari.flyt.audit.authorization.AuthorizedUserDto
import no.novari.flyt.audit.entity.AuditTestConfig
import no.novari.flyt.audit.revision.RevisedTestEntity
import no.novari.flyt.audit.revision.RevisedTestEntityRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
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

    private lateinit var historyService: EnversHistoryService<RevisedTestEntity, Long, RevisedTestEntity>

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM revised_test_entity_aud")
        jdbcTemplate.execute("DELETE FROM revinfo")
        jdbcTemplate.execute("DELETE FROM revised_test_entity")

        historyService =
            object : EnversHistoryService<RevisedTestEntity, Long, RevisedTestEntity>(
                RevisedTestEntity::class.java,
                entityManager,
                ActorDisplayResolver(HttpActorNameLookup(fakeClient), ActorDisplayProperties()),
            ) {
                override fun mapSnapshot(entity: RevisedTestEntity) = entity
            }
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `history is newest first with the right type and resolved name`() {
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
    fun `deletion gives a DELETED entry with a null snapshot`() {
        setJwtWithOid(userOid)
        val saved = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })
        val id = saved.id!!

        entityRepository.delete(saved)
        entityRepository.flush()

        val page = findHistory(id, PageRequest.of(0, 20))

        assertThat(page.content.first().type).isEqualTo(HistoryEventType.DELETED)
        assertThat(page.content.first().snapshot).isNull()
    }

    // Controllers call the service without a transaction. Open-in-view does not help for a persistence unit that is
    // not the primary one, so the service must not depend on a transaction-bound EntityManager.
    @Test
    fun `history can be read outside a transaction`() {
        setJwtWithOid(userOid)
        val saved = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })
        saved.name = "v2"
        entityRepository.saveAndFlush(saved)

        val page = historyService.findHistory(saved.id!!, PageRequest.of(0, 20))

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content.map { it.snapshot?.name }).containsExactly("v2", "v1")
    }

    @Test
    fun `all history with a property filter can be read outside a transaction`() {
        setJwtWithOid(userOid)
        val kept = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "kept" })
        entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "filtered" })

        val page =
            historyService.findAllHistory(
                PageRequest.of(0, 20),
                propertyFilter = AuditPropertyFilter(property = "name", allowedValues = listOf("kept")),
            )

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content.single().entityId).isEqualTo(kept.id)
    }

    @Test
    fun `System actor gets its display value from ActorDisplayResolver`() {
        SecurityContextHolder.clearContext()
        val saved = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })
        val id = saved.id!!

        val page = findHistory(id, PageRequest.of(0, 20))

        assertThat(page.content.single().actor).isEqualTo(Actor.System)
        assertThat(page.content.single().actorDisplay).isEqualTo("System")
    }

    @Test
    fun `paging limits the content but counts the total`() {
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
    fun `from filter excludes older revisions`() {
        setJwtWithOid(userOid)
        val saved = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })
        val id = saved.id!!

        val future = Instant.now().plusSeconds(3600)
        val page = findHistory(id, PageRequest.of(0, 20), HistoryFilter(from = future))

        assertThat(page.totalElements).isZero()
        assertThat(page.content).isEmpty()
    }

    @Test
    fun `findAllHistory returns history for all entities with entityId`() {
        setJwtWithOid(userOid)
        val a = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "a" })
        val b = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "b" })

        val page = findAllHistory(PageRequest.of(0, 20))

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content.map { it.entityId }).containsExactlyInAnyOrder(a.id, b.id)
    }

    @Test
    fun `findAllHistory sorts newest revision first across entities`() {
        setJwtWithOid(userOid)
        val a = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "a1" })
        a.name = "a2"
        entityRepository.saveAndFlush(a)
        val b = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "b1" })

        val page = findAllHistory(PageRequest.of(0, 20))

        val snapshot = page.content.first().snapshot
        assertThat(snapshot?.name).isEqualTo("b1")
    }

    @Test
    fun `findAllHistory DEL entry has entityId and a null snapshot`() {
        setJwtWithOid(userOid)
        val saved = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })
        val id = saved.id!!
        entityRepository.delete(saved)
        entityRepository.flush()

        val page = findAllHistory(PageRequest.of(0, 20))

        val deleted = page.content.first { it.type == HistoryEventType.DELETED }
        assertThat(deleted.entityId).isEqualTo(id)
        assertThat(deleted.snapshot).isNull()
    }

    @Test
    fun `findAllHistory paging limits the content but counts the total`() {
        setJwtWithOid(userOid)
        repeat(3) { i ->
            entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "e$i" })
        }

        val page = findAllHistory(PageRequest.of(0, 2))

        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content).hasSize(2)
    }

    @Test
    fun `findAllHistory from filter excludes older revisions`() {
        setJwtWithOid(userOid)
        entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })

        val future = Instant.now().plusSeconds(3600)
        val page = findAllHistory(PageRequest.of(0, 20), HistoryFilter(from = future))

        assertThat(page.totalElements).isZero()
        assertThat(page.content).isEmpty()
    }

    @Test
    fun `findAllHistory resolves actorDisplay for a user actor`() {
        setJwtWithOid(userOid)
        entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "v1" })

        val page = findAllHistory(PageRequest.of(0, 20))

        assertThat(page.content.single().actorDisplay).isEqualTo("Ola Nordmann")
        assertThat(page.content.single().actor).isEqualTo(Actor.User(userOid))
    }

    @Test
    fun `findAllHistory with propertyFilter limits to matching entities`() {
        setJwtWithOid(userOid)
        val a = entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "keep" })
        entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "exclude" })

        val page = findAllHistory(PageRequest.of(0, 20), propertyFilter = AuditPropertyFilter("name", listOf("keep")))

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content.single().entityId).isEqualTo(a.id)
    }

    @Test
    fun `findAllHistory without propertyFilter returns everything`() {
        setJwtWithOid(userOid)
        entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "a" })
        entityRepository.saveAndFlush(RevisedTestEntity().apply { name = "b" })

        val page = findAllHistory(PageRequest.of(0, 20), propertyFilter = null)

        assertThat(page.totalElements).isEqualTo(2)
    }

    @Test
    fun `findAllHistory with propertyFilter includes the delete revision (store_data_at_delete)`() {
        setJwtWithOid(userOid)
        val saved =
            entityRepository.saveAndFlush(
                RevisedTestEntity().apply {
                    name = "v1"
                    tenantId = 1L
                },
            )
        val id = saved.id!!
        saved.name = "v2"
        entityRepository.saveAndFlush(saved)
        entityRepository.delete(saved)
        entityRepository.flush()

        val page = findAllHistory(PageRequest.of(0, 20), propertyFilter = AuditPropertyFilter("tenantId", listOf(1L)))

        val entries = page.content.filter { it.entityId == id }
        assertThat(entries.map { it.type })
            .containsExactly(HistoryEventType.DELETED, HistoryEventType.UPDATED, HistoryEventType.CREATED)
        val deleted = entries.first { it.type == HistoryEventType.DELETED }
        assertThat(deleted.snapshot).isNull()
    }

    @Test
    fun `findAllHistory with empty allowedValues gives an empty page without querying the database`() {
        setJwtWithOid(userOid)
        entityRepository.saveAndFlush(
            RevisedTestEntity().apply {
                name = "a"
                tenantId = 1L
            },
        )

        val page = findAllHistory(PageRequest.of(0, 20), propertyFilter = AuditPropertyFilter("tenantId", emptyList()))

        assertThat(page.totalElements).isZero()
        assertThat(page.content).isEmpty()
    }

    private fun findHistory(
        id: Long,
        pageable: PageRequest,
        filter: HistoryFilter = HistoryFilter(),
    ) = transactionTemplate.execute {
        historyService.findHistory(id, pageable, filter)
    }

    private fun findAllHistory(
        pageable: PageRequest,
        filter: HistoryFilter = HistoryFilter(),
        propertyFilter: AuditPropertyFilter? = null,
    ) = transactionTemplate.execute {
        historyService.findAllHistory(pageable, filter, propertyFilter)
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
