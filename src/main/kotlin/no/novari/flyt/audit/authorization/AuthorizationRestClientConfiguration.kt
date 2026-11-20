package no.novari.flyt.audit.authorization

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration
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
 * A separate `@AutoConfiguration` (rather than a plain `@Configuration` imported with `@Import`), so that
 * [FlytAuditAutoConfiguration] can use `@AutoConfigureAfter` to make sure that the
 * `authorizationRestClient` bean is registered before its `@ConditionalOnBean(name = [...])`
 * is evaluated. `@AutoConfigureAfter(OAuth2ClientAutoConfiguration::class)` likewise makes sure
 * that `ClientRegistrationRepository`/`OAuth2AuthorizedClientService` from Spring Boot's own
 * auto-configuration are registered before this class's `@ConditionalOnBean` checks run.
 * `@ConditionalOnBean` across auto-configurations without an explicit order is not
 * reliable, see the Spring Boot documentation on "creating your own auto-configuration".
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
        logger.info("Registering authorizationAuthorizedClientManager")
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
     * `ClientHttpRequestFactory` is not a standard Spring Boot bean, so it is injected as an
     * [ObjectProvider]. Apps without their own factory get the builder default
     * instead of failing at startup.
     */
    @Bean("authorizationRestClient")
    @ConditionalOnBean(OAuth2AuthorizedClientManager::class)
    fun authorizationRestClient(
        authorizationAuthorizedClientManager: OAuth2AuthorizedClientManager,
        clientHttpRequestFactory: ObjectProvider<ClientHttpRequestFactory>,
        restClientBuilder: RestClient.Builder,
        props: AuthorizationProperties,
    ): RestClient {
        logger.info("Registering authorizationRestClient (baseUrl={})", props.baseUrl)
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
        logger.info("Registering AuthorizationClient (cache={})", props.cache.enabled)
        val base: AuthorizationClient = RestClientAuthorizationClient(restClient)
        return if (props.cache.enabled) CachingAuthorizationClient(base, props.cache) else base
    }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(AuthorizationRestClientConfiguration::class.java)
    }
}
