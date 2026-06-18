package no.novari.flyt.audit.authorization

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor
import org.springframework.web.client.RestClient

@Configuration
@ConditionalOnClass(ClientRegistrationRepository::class)
class AuthorizationRestClientConfiguration {
    @Bean
    @ConditionalOnBean(ClientRegistrationRepository::class)
    fun authorizationAuthorizedClientManager(
        clientRegistrationRepository: ClientRegistrationRepository,
        authorizedClientService: OAuth2AuthorizedClientService,
    ): OAuth2AuthorizedClientManager {
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

    @Bean("authorizationRestClient")
    @ConditionalOnBean(OAuth2AuthorizedClientManager::class)
    fun authorizationRestClient(
        authorizationAuthorizedClientManager: OAuth2AuthorizedClientManager,
        clientHttpRequestFactory: ClientHttpRequestFactory,
        restClientBuilder: RestClient.Builder,
        props: AuthorizationProperties,
    ): RestClient {
        val interceptor = OAuth2ClientHttpRequestInterceptor(authorizationAuthorizedClientManager)
        interceptor.setClientRegistrationIdResolver { props.clientRegistrationId }

        return restClientBuilder
            .requestInterceptor(interceptor)
            .requestFactory(clientHttpRequestFactory)
            .baseUrl("${props.baseUrl}/api/intern-klient/authorization/users")
            .build()
    }
}
