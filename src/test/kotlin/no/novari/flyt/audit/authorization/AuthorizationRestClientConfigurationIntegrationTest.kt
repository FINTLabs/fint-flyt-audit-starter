package no.novari.flyt.audit.authorization

import no.novari.flyt.audit.actor.ActorDisplayResolver
import no.novari.flyt.audit.actor.ActorNameLookup
import no.novari.flyt.audit.actor.HttpActorNameLookup
import no.novari.flyt.audit.config.FlytAuditAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Regression test for the rc-10 and rc-11 bugs: without
 * `@AutoConfigureAfter(OAuth2ClientAutoConfiguration::class)`,
 * `ClientRegistrationRepository` could be registered after this class's
 * `@ConditionalOnBean` checks, so apps silently fell back to
 * `NoOpActorNameLookup` even with a correct OAuth2 setup.
 *
 * The test uses only real auto-configurations ([OAuth2ClientAutoConfiguration],
 * [RestClientAutoConfiguration]) and properties, no manually registered beans.
 * Manually registered beans are always processed before auto-configuration and hide both
 * ordering bugs and missing bean dependencies (such as the `ClientHttpRequestFactory` requirement
 * that broke startup in rc-11).
 */
class AuthorizationRestClientConfigurationIntegrationTest {
    @Test
    fun `the chain from Spring Boot's auto-configurations to HttpActorNameLookup is wired`() {
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
