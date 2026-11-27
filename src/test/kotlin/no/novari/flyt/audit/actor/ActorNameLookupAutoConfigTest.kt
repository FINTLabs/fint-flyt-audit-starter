package no.novari.flyt.audit.actor

import no.novari.flyt.audit.authorization.AuthorizationClient
import no.novari.flyt.audit.config.FlytAuditAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.getBeansOfType
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
    fun `HttpActorNameLookup is registered when an AuthorizationClient exists`() {
        contextRunner
            .withBean(AuthorizationClient::class.java, { mock(AuthorizationClient::class.java) })
            .run { context ->
                assertThat(context).hasSingleBean(ActorNameLookup::class.java)
                assertThat(context.getBean<ActorNameLookup>())
                    .isInstanceOf(HttpActorNameLookup::class.java)
                assertThat(context).hasSingleBean(ActorDisplayResolver::class.java)
            }
    }

    @Test
    fun `a custom ActorNameLookup bean overrides the default HTTP lookup`() {
        contextRunner
            .withBean(AuthorizationClient::class.java, { mock(AuthorizationClient::class.java) })
            .withUserConfiguration(LocalLookupConfig::class.java)
            .run { context ->
                assertThat(context.getBeansOfType<ActorNameLookup>()).hasSize(1)
                assertThat(context.getBean<ActorNameLookup>())
                    .isNotInstanceOf(HttpActorNameLookup::class.java)
                assertThat(context).hasSingleBean(ActorDisplayResolver::class.java)
            }
    }

    @Test
    fun `without AuthorizationClient the lookup falls back to no-op, but the resolver is still registered`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(ActorNameLookup::class.java)
            assertThat(context.getBean<ActorNameLookup>())
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
