package no.novari.flyt.audit.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorAuditorAware
import no.novari.flyt.audit.actor.ActorDisplayResolver
import no.novari.flyt.audit.authorization.AuthorizationClient
import no.novari.flyt.audit.metrics.AuditMetrics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import java.util.function.Supplier

class FlytAuditAutoConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlytAuditAutoConfiguration::class.java))

    @Test
    fun `flytAuditorAware-bønne registreres som ActorAuditorAware`() {
        contextRunner.run { context ->
            assertThat(context).hasBean("flytAuditorAware")
            assertThat(context.getBean("flytAuditorAware")).isInstanceOf(ActorAuditorAware::class.java)
        }
    }

    @Test
    fun `flytAuditorAware kan overstyres av konsument`() {
        contextRunner
            .withUserConfiguration(CustomAuditorAwareConfig::class.java)
            .run { context ->
                assertThat(context.getBeansOfType(AuditorAware::class.java)).hasSize(1)
                assertThat(context.getBean("flytAuditorAware")).isInstanceOf(ActorAuditorAware::class.java)
            }
    }

    @Test
    fun `applicationContextHolder-bønne registreres`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(ApplicationContextHolder::class.java)
        }
    }

    @Test
    fun `auditMetrics-bønne registreres når MeterRegistry er tilgjengelig`() {
        contextRunner
            .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() as MeterRegistry })
            .run { context ->
                assertThat(context).hasSingleBean(AuditMetrics::class.java)
            }
    }

    @Test
    fun `auditMetrics-bønne registreres ikke uten MeterRegistry`() {
        contextRunner.run { context ->
            assertThat(context).doesNotHaveBean(AuditMetrics::class.java)
        }
    }

    @Test
    fun `actorDisplayResolver-bønne registreres via no-op lookup uten authorizationClient`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(ActorDisplayResolver::class.java)
        }
    }

    @Test
    fun `actorDisplayResolver-bønne registreres når AuthorizationClient er tilgjengelig`() {
        contextRunner
            .withBean(
                AuthorizationClient::class.java,
                { mock(AuthorizationClient::class.java) },
            ).run { context ->
                assertThat(context).hasSingleBean(ActorDisplayResolver::class.java)
            }
    }

    @Test
    fun `store_data_at_delete settes til true av HibernatePropertiesCustomizer`() {
        contextRunner.run { context ->
            val customizer = context.getBean<HibernatePropertiesCustomizer>()
            val properties = mutableMapOf<String, Any>()
            customizer.customize(properties)
            assertThat(properties["org.hibernate.envers.store_data_at_delete"]).isEqualTo("true")
        }
    }

    @Test
    fun `store_data_at_delete overstyres ikke når konsumenten allerede har satt den`() {
        contextRunner.run { context ->
            val customizer = context.getBean<HibernatePropertiesCustomizer>()
            val properties = mutableMapOf<String, Any>("org.hibernate.envers.store_data_at_delete" to "false")
            customizer.customize(properties)
            assertThat(properties["org.hibernate.envers.store_data_at_delete"]).isEqualTo("false")
        }
    }

    @Configuration
    class CustomAuditorAwareConfig {
        @Bean("flytAuditorAware")
        fun flytAuditorAware(): AuditorAware<Actor> = ActorAuditorAware()
    }
}
