package no.novari.flyt.audit.authorization

import no.novari.flyt.audit.actor.ActorDisplayResolver
import no.novari.flyt.audit.actor.ActorNameLookup
import no.novari.flyt.audit.actor.HttpActorNameLookup
import no.novari.flyt.audit.config.FlytAuditAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository

/**
 * Reproduserer discovery-service sin faktiske oppsett: en profil-spesifikk
 * `application-{profil}.yaml` lastet via `spring.profiles.active` gjennom en ekte
 * `SpringApplication` (IKKE via `withPropertyValues` direkte som i
 * [AuthorizationRestClientConfigurationIntegrationTest]), med placeholder-verdier
 * satt som System properties (samme oppløsningsmekanisme som miljøvariabler med
 * punktum i navnet).
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
    fun `application-oauth2-repro yaml gir ClientRegistrationRepository via ekte auto-config-sortering`() {
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
}
