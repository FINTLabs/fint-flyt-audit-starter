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
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Verifiserer at @EnableFlytAuditing aktiverer Spring Data JPA Auditing i en full
 * Spring Boot-kontekst (samme oppsett som en produksjons-konsument bruker).
 *
 * Sliceduken `@DataJpaTest` har egen JpaAuditingTestConfig som dekker test-tilfellet —
 * men det betyr at slice-tester IKKE ville oppdaget hvis @EnableFlytAuditing brøt
 * auto-config-ordningen i prod. Denne testen lukker det hullet ved å bruke
 * @SpringBootTest med en konsument som etterligner produksjons-mønsteret:
 * @SpringBootApplication + @EnableFlytAuditing.
 */
@SpringBootTest(classes = [EnableFlytAuditingProdLikeIntegrationTest.ConsumerApplication::class])
@Testcontainers
@Transactional
class EnableFlytAuditingProdLikeIntegrationTest {
    @SpringBootApplication(scanBasePackages = ["no.novari.flyt.audit"])
    @EnableFlytAuditing
    class ConsumerApplication

    @Autowired
    private lateinit var repository: AuditedTestEntityRepository

    @Test
    fun `JPA Auditing aktiveres når @EnableFlytAuditing brukes på en SpringBootApplication`() {
        val saved = repository.saveAndFlush(AuditedTestEntity().apply { value = "test" })

        assertThat(saved.createdBy)
            .isEqualTo(Actor.System)
            .withFailMessage(
                "createdBy ble ikke populert av Spring Data JPA Auditing. " +
                    "Sannsynligvis fordi @EnableJpaAuditing ikke ble aktivert — " +
                    "auto-config-ordering bruteren av @EnableFlytAuditing sin @Import-mekanisme.",
            )
        assertThat(saved.createdAt).isNotNull
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
    }
}
