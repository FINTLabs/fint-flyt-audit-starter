package no.novari.flyt.audit.actor

import no.novari.flyt.audit.authorization.AuthorizationClient
import no.novari.flyt.audit.config.FlytAuditAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

class ActorNameLookupAutoConfigTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlytAuditAutoConfiguration::class.java))

    @Test
    fun `HttpActorNameLookup registreres når AuthorizationClient er tilgjengelig`() {
        contextRunner
            .withBean(AuthorizationClient::class.java, { mock(AuthorizationClient::class.java) })
            .run { context ->
                assertThat(context).hasSingleBean(ActorNameLookup::class.java)
                assertThat(context.getBean(ActorNameLookup::class.java))
                    .isInstanceOf(HttpActorNameLookup::class.java)
                assertThat(context).hasSingleBean(ActorDisplayResolver::class.java)
            }
    }

    @Test
    fun `egen ActorNameLookup-bønne overstyrer default HTTP-versjon`() {
        contextRunner
            .withBean(AuthorizationClient::class.java, { mock(AuthorizationClient::class.java) })
            .withUserConfiguration(LocalLookupConfig::class.java)
            .run { context ->
                assertThat(context.getBeansOfType(ActorNameLookup::class.java)).hasSize(1)
                assertThat(context.getBean(ActorNameLookup::class.java))
                    .isNotInstanceOf(HttpActorNameLookup::class.java)
                assertThat(context).hasSingleBean(ActorDisplayResolver::class.java)
            }
    }

    @Test
    fun `uten AuthorizationClient faller lookup tilbake til no-op, men resolver registreres fortsatt`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(ActorNameLookup::class.java)
            assertThat(context.getBean(ActorNameLookup::class.java))
                .isInstanceOf(NoOpActorNameLookup::class.java)
            assertThat(context).hasSingleBean(ActorDisplayResolver::class.java)
        }
    }

    @Configuration
    class LocalLookupConfig {
        @Bean
        fun localLookup(): ActorNameLookup = ActorNameLookup { oids -> oids.associateWith<UUID, String?> { "lokal" } }
    }
}
