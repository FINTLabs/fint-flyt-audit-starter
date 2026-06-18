package no.novari.flyt.audit.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.novari.flyt.audit.config.ApplicationContextHolder
import no.novari.flyt.audit.entity.AuditTestConfig
import no.novari.flyt.audit.entity.AuditedTestEntity
import no.novari.flyt.audit.entity.AuditedTestEntityRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
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
@Import(AuditTestConfig::class, ApplicationContextHolder::class, AuditMetricsListenerIntegrationTest.MetricsConfig::class)
class AuditMetricsListenerIntegrationTest {
    @TestConfiguration
    class MetricsConfig {
        @Bean
        fun meterRegistry() = SimpleMeterRegistry()

        @Bean
        fun auditMetrics(registry: SimpleMeterRegistry) = AuditMetrics(registry)
    }

    @Autowired
    lateinit var repository: AuditedTestEntityRepository

    @Autowired
    lateinit var registry: SimpleMeterRegistry

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `AuditMetricsListener inkrementerer success-teller ved lagring`() {
        setJwt()
        repository.saveAndFlush(AuditedTestEntity().apply { value = "first" })

        val count =
            registry
                .counter(AuditMetrics.WRITE_COUNTER, "entity", "AuditedTestEntity", "outcome", "success")
                .count()
        assertThat(count).isGreaterThanOrEqualTo(1.0)
    }

    @Test
    fun `AuditMetricsListener inkrementerer teller ved oppdatering`() {
        setJwt()
        val saved = repository.saveAndFlush(AuditedTestEntity().apply { value = "initial" })
        val afterInsert =
            registry
                .counter(AuditMetrics.WRITE_COUNTER, "entity", "AuditedTestEntity", "outcome", "success")
                .count()

        saved.value = "updated"
        repository.saveAndFlush(saved)

        val afterUpdate =
            registry
                .counter(AuditMetrics.WRITE_COUNTER, "entity", "AuditedTestEntity", "outcome", "success")
                .count()
        assertThat(afterUpdate).isGreaterThan(afterInsert)
    }

    @Test
    fun `AuditMetricsListener registrerer timer-observasjon`() {
        setJwt()
        repository.saveAndFlush(AuditedTestEntity().apply { value = "v1" })

        val timerCount =
            registry
                .timer(AuditMetrics.WRITE_DURATION, "entity", "AuditedTestEntity")
                .count()
        assertThat(timerCount).isGreaterThanOrEqualTo(1L)
    }

    private fun setJwt() {
        val jwt =
            Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                mapOf("alg" to "RS256"),
                mapOf("oid" to UUID.randomUUID().toString()),
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
