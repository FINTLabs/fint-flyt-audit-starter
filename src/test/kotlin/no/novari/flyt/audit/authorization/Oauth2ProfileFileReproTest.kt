package no.novari.flyt.audit.authorization

import no.novari.flyt.audit.actor.ActorDisplayResolver
import no.novari.flyt.audit.actor.ActorNameLookup
import no.novari.flyt.audit.actor.HttpActorNameLookup
import no.novari.flyt.audit.config.FlytAuditAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository

/**
 * Reproduces the setup discovery-service used: a profile-specific
 * `application-{profile}.yaml` loaded with `spring.profiles.active` through a real
 * `SpringApplication` (not with `withPropertyValues` as in
 * [AuthorizationRestClientConfigurationIntegrationTest]), with placeholder values
 * set as system properties (resolved the same way as environment variables with
 * dots in their names).
 */
class Oauth2ProfileFileReproTest {
    @Configuration
    @EnableAutoConfiguration(
        exclude = [
            DataSourceAutoConfiguration::class,
            HibernateJpaAutoConfiguration::class,
        ],
    )
    class TestApp

    @Test
    fun `application-oauth2-repro yaml gives a ClientRegistrationRepository through the real auto-configuration order`() {
        System.setProperty("fint.flyt.authorization.sso.client-id", "test-client")
        System.setProperty("fint.flyt.authorization.sso.client-secret", "test-secret")
        val context =
            SpringApplicationBuilder(TestApp::class.java)
                .web(WebApplicationType.NONE)
                .profiles("oauth2-repro")
                .run()
        try {
            assertThat(context.getBeansOfType(ClientRegistrationRepository::class.java)).hasSize(1)
            assertThat(context.containsBean("authorizationRestClient")).isTrue()
            assertThat(context.getBean(ActorNameLookup::class.java))
                .isInstanceOf(HttpActorNameLookup::class.java)
            assertThat(context.getBeansOfType(ActorDisplayResolver::class.java)).hasSize(1)
        } finally {
            context.close()
            System.clearProperty("fint.flyt.authorization.sso.client-id")
            System.clearProperty("fint.flyt.authorization.sso.client-secret")
        }
    }

    @Test
    fun `missing client id and secret does not break the context`() {
        val context =
            SpringApplicationBuilder(TestApp::class.java)
                .web(WebApplicationType.NONE)
                .profiles("oauth2-repro")
                .run()
        context.close()
    }
}
