package no.novari.flyt.audit.authorization

import no.novari.flyt.audit.actor.ActorDisplayResolver
import no.novari.flyt.audit.actor.ActorNameLookup
import no.novari.flyt.audit.actor.HttpActorNameLookup
import no.novari.flyt.audit.config.FlytAuditAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Regresjonstest for rc-10/rc-11-bugene: uten
 * `@AutoConfigureAfter(OAuth2ClientAutoConfiguration::class)` kunne
 * `ClientRegistrationRepository` bli registrert etter denne klassens
 * `@ConditionalOnBean`-sjekker, som gjorde at konsumenter falt stille tilbake til
 * `NoOpActorNameLookup` selv med korrekt OAuth2-oppsett.
 *
 * Testen bruker kun ekte auto-konfigurasjoner ([OAuth2ClientAutoConfiguration],
 * [RestClientAutoConfiguration]) og properties — ingen manuelt registrerte støtte-bønner.
 * Manuelt registrerte bønner prosesseres alltid før auto-konfigurasjon og skjuler både
 * rekkefølge-bugs og manglende bønne-avhengigheter (som `ClientHttpRequestFactory`-kravet
 * som knakk oppstart i rc-11).
 */
class AuthorizationRestClientConfigurationIntegrationTest {
    @Test
    fun `hele kjeden fra Spring Boots egne auto-konfigurasjoner til HttpActorNameLookup kobles sammen`() {
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    OAuth2ClientAutoConfiguration::class.java,
                    RestClientAutoConfiguration::class.java,
                    AuthorizationRestClientConfiguration::class.java,
                    FlytAuditAutoConfiguration::class.java,
                ),
            ).withPropertyValues(
                "spring.security.oauth2.client.registration.authorization-service.client-id=test-client",
                "spring.security.oauth2.client.registration.authorization-service.client-secret=test-secret",
                "spring.security.oauth2.client.registration.authorization-service.authorization-grant-type=client_credentials",
                "spring.security.oauth2.client.registration.authorization-service.provider=fint-idp",
                "spring.security.oauth2.client.provider.fint-idp.token-uri=http://localhost/token",
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasBean("authorizationRestClient")
                assertThat(context).hasSingleBean(AuthorizationClient::class.java)
                assertThat(context.getBean(ActorNameLookup::class.java))
                    .isInstanceOf(HttpActorNameLookup::class.java)
                assertThat(context).hasSingleBean(ActorDisplayResolver::class.java)
            }
    }
}
