package no.novari.flyt.audit.authorization

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor
import org.springframework.web.client.RestClient

/**
 * Egen `@AutoConfiguration` (fremfor plain `@Configuration` importert via `@Import`) slik at
 * [FlytAuditAutoConfiguration] kan bruke `@AutoConfigureAfter` for å garantere at
 * `authorizationRestClient`-bønnen er registrert før dens `@ConditionalOnBean(name = [...])`
 * evalueres. `@AutoConfigureAfter(OAuth2ClientAutoConfiguration::class)` her sikrer tilsvarende
 * at `ClientRegistrationRepository`/`OAuth2AuthorizedClientService` fra Spring Boots egen
 * autokonfigurasjon er registrert FØR denne klassens `@ConditionalOnBean`-sjekker evalueres.
 * `@ConditionalOnBean` på tvers av auto-config-klasser uten eksplisitt rekkefølge er ikke
 * pålitelig — se Spring Boot-dokumentasjonen om "creating your own auto-configuration".
 */
@AutoConfiguration
@AutoConfigureAfter(OAuth2ClientAutoConfiguration::class)
@ConditionalOnClass(ClientRegistrationRepository::class)
@EnableConfigurationProperties(AuthorizationProperties::class)
class AuthorizationRestClientConfiguration {
    @Bean
    @ConditionalOnBean(ClientRegistrationRepository::class)
    fun authorizationAuthorizedClientManager(
        clientRegistrationRepository: ClientRegistrationRepository,
        authorizedClientService: OAuth2AuthorizedClientService,
    ): OAuth2AuthorizedClientManager {
        logger.info("Registrerer authorizationAuthorizedClientManager")
        val manager =
            AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository,
                authorizedClientService,
            )
        manager.setAuthorizedClientProvider(
            OAuth2AuthorizedClientProviderBuilder
                .builder()
                .clientCredentials()
                .refreshToken()
                .build(),
        )
        return manager
    }

    /**
     * `ClientHttpRequestFactory` er ikke en standard Spring Boot-bønne — den injiseres som
     * [ObjectProvider] slik at konsumenter uten en egendefinert factory faller tilbake til
     * builder-defaulten i stedet for å feile ved oppstart.
     */
    @Bean("authorizationRestClient")
    @ConditionalOnBean(OAuth2AuthorizedClientManager::class)
    fun authorizationRestClient(
        authorizationAuthorizedClientManager: OAuth2AuthorizedClientManager,
        clientHttpRequestFactory: ObjectProvider<ClientHttpRequestFactory>,
        restClientBuilder: RestClient.Builder,
        props: AuthorizationProperties,
    ): RestClient {
        logger.info("Registrerer authorizationRestClient (baseUrl={})", props.baseUrl)
        val interceptor = OAuth2ClientHttpRequestInterceptor(authorizationAuthorizedClientManager)
        interceptor.setClientRegistrationIdResolver { props.clientRegistrationId }

        val builder =
            restClientBuilder
                .requestInterceptor(interceptor)
                .baseUrl("${props.baseUrl}/api/intern-klient/authorization/users")
        clientHttpRequestFactory.ifAvailable { builder.requestFactory(it) }
        return builder.build()
    }

    @Bean
    @ConditionalOnBean(name = ["authorizationRestClient"])
    @ConditionalOnMissingBean(AuthorizationClient::class)
    fun authorizationClient(
        @Qualifier("authorizationRestClient") restClient: RestClient,
        props: AuthorizationProperties,
    ): AuthorizationClient {
        logger.info("Registrerer AuthorizationClient (cache={})", props.cache.enabled)
        val base: AuthorizationClient = RestClientAuthorizationClient(restClient)
        return if (props.cache.enabled) CachingAuthorizationClient(base, props.cache) else base
    }

    private companion object {
        val logger = LoggerFactory.getLogger(AuthorizationRestClientConfiguration::class.java)
    }
}
