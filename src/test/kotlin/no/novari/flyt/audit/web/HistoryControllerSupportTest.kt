package no.novari.flyt.audit.web

import jakarta.persistence.EntityManager
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.authorization.AuthorizationClient
import no.novari.flyt.audit.history.ActorEnrichmentService
import no.novari.flyt.audit.history.EnversHistoryService
import no.novari.flyt.audit.history.HistoryEntryDto
import no.novari.flyt.audit.history.HistoryEventType
import no.novari.flyt.audit.history.HistoryFilter
import no.novari.flyt.audit.revision.RevisedTestEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
@Import(HistoryControllerSupportTest.TestConfig::class)
class HistoryControllerSupportTest {
    @RestController
    @RequestMapping("/test-entities")
    class TestHistoryController(
        service: EnversHistoryService<RevisedTestEntity, Long>,
    ) : HistoryControllerSupport<RevisedTestEntity, Long>(service)

    class FakeHistoryService(
        var canned: Page<HistoryEntryDto<RevisedTestEntity>> = PageImpl(emptyList()),
    ) : EnversHistoryService<RevisedTestEntity, Long>(
            RevisedTestEntity::class.java,
            mock(EntityManager::class.java),
            ActorEnrichmentService(mock(AuthorizationClient::class.java)),
        ) {
        var lastId: Long? = null
        var lastPageable: Pageable? = null
        var lastFilter: HistoryFilter? = null

        override fun findHistory(
            id: Long,
            pageable: Pageable,
            filter: HistoryFilter,
        ): Page<HistoryEntryDto<RevisedTestEntity>> {
            lastId = id
            lastPageable = pageable
            lastFilter = filter
            return canned
        }
    }

    class TestConfig {
        @Bean
        fun fakeHistoryService() = FakeHistoryService()

        @Bean
        fun testHistoryController(service: FakeHistoryService) = TestHistoryController(service)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var fakeHistoryService: FakeHistoryService

    @Test
    fun `history-endepunkt mapper id og returnerer innhold`() {
        val oid = UUID.randomUUID()
        fakeHistoryService.canned =
            PageImpl(
                listOf(
                    HistoryEntryDto(
                        timestamp = Instant.parse("2026-06-17T10:00:00Z"),
                        type = HistoryEventType.UPDATED,
                        actor = Actor.User(oid),
                        actorDisplay = "Ola Nordmann",
                        snapshot = RevisedTestEntity().apply { name = "v2" },
                    ),
                ),
                PageRequest.of(0, 20),
                1L,
            )

        mockMvc
            .get("/test-entities/42/history")
            .andExpect {
                status { isOk() }
                jsonPath("$.content[0].type") { value("UPDATED") }
                jsonPath("$.content[0].actorDisplay") { value("Ola Nordmann") }
                jsonPath("$.content[0].actor.type") { value("USER") }
                jsonPath("$.content[0].actor.oid") { value(oid.toString()) }
                jsonPath("$.content[0].snapshot.name") { value("v2") }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.totalPages") { value(1) }
                jsonPath("$.page") { value(0) }
                jsonPath("$.size") { value(20) }
            }

        assertThat(fakeHistoryService.lastId).isEqualTo(42L)
    }

    @Test
    fun `pageable-default settes når ingen parametere er gitt`() {
        mockMvc.get("/test-entities/1/history").andExpect { status { isOk() } }

        val pageable = fakeHistoryService.lastPageable!!
        assertThat(pageable.pageSize).isEqualTo(20)
        assertThat(pageable.pageNumber).isZero()
    }

    @Test
    fun `from- og to-parametere bindes til filteret`() {
        mockMvc
            .get("/test-entities/1/history") {
                param("from", "2026-06-01T00:00:00Z")
                param("to", "2026-06-30T00:00:00Z")
            }.andExpect { status { isOk() } }

        val filter = fakeHistoryService.lastFilter!!
        assertThat(filter.from).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"))
        assertThat(filter.to).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"))
    }

    @Test
    fun `tomt filter når ingen tidsparametere er gitt`() {
        mockMvc.get("/test-entities/1/history").andExpect { status { isOk() } }

        val filter = fakeHistoryService.lastFilter!!
        assertThat(filter.from).isNull()
        assertThat(filter.to).isNull()
    }
}
