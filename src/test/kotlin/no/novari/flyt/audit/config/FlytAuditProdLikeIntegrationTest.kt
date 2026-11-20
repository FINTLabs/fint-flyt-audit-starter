package no.novari.flyt.audit.config

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.entity.AuditedTestEntity
import no.novari.flyt.audit.entity.AuditedTestEntityRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Verifies that JPA auditing is active in a full Spring Boot application, through the auto-configurations alone.
 * `@DataJpaTest` slices use their own auditing configuration and would not catch a broken auto-configuration order.
 */
@SpringBootTest(classes = [FlytAuditProdLikeIntegrationTest.ConsumerApplication::class])
@Testcontainers
@Transactional
class FlytAuditProdLikeIntegrationTest {
    @SpringBootApplication(scanBasePackages = ["no.novari.flyt.audit"])
    class ConsumerApplication

    @Autowired
    private lateinit var repository: AuditedTestEntityRepository

    @Test
    fun `JPA auditing sets createdBy and createdAt in a Spring Boot application`() {
        val saved = repository.saveAndFlush(AuditedTestEntity().apply { value = "test" })

        assertThat(saved.createdBy)
            .withFailMessage("createdBy was not set, so FlytAuditJpaAuditingAutoConfiguration did not enable JPA auditing")
            .isEqualTo(Actor.System)
        assertThat(saved.createdAt).isNotNull
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine")
    }
}
