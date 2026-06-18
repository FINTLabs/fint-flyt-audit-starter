package no.novari.flyt.audit.config

import io.micrometer.core.instrument.MeterRegistry
import jakarta.persistence.EntityManagerFactory
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorAuditorAware
import no.novari.flyt.audit.authorization.AuthorizationClient
import no.novari.flyt.audit.authorization.AuthorizationProperties
import no.novari.flyt.audit.authorization.AuthorizationRestClientConfiguration
import no.novari.flyt.audit.authorization.CachingAuthorizationClient
import no.novari.flyt.audit.authorization.RestClientAuthorizationClient
import no.novari.flyt.audit.history.ActorEnrichmentService
import no.novari.flyt.audit.metrics.AuditMetrics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.web.client.RestClient

@AutoConfiguration
@ConditionalOnClass(AuditingEntityListener::class)
@EnableConfigurationProperties(AuthorizationProperties::class)
@Import(AuthorizationRestClientConfiguration::class)
class FlytAuditAutoConfiguration {
    /**
     * Aktiverer JPA Auditing kun når en EntityManagerFactory er tilgjengelig.
     * @ConditionalOnMissingBean(name = ["jpaAuditingHandler"]) hindrer konflikt
     * hvis konsumenten har sin egen @EnableJpaAuditing.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(EntityManagerFactory::class)
    @ConditionalOnMissingBean(name = ["jpaAuditingHandler"])
    @EnableJpaAuditing(auditorAwareRef = "flytAuditorAware")
    class JpaAuditingConfiguration

    @Bean
    @ConditionalOnMissingBean(name = ["flytAuditorAware"])
    fun flytAuditorAware(): AuditorAware<Actor> = ActorAuditorAware()

    @Bean
    @ConditionalOnMissingBean
    fun applicationContextHolder() = ApplicationContextHolder()

    @Bean
    @ConditionalOnBean(name = ["authorizationRestClient"])
    @ConditionalOnMissingBean(AuthorizationClient::class)
    fun authorizationClient(
        @Qualifier("authorizationRestClient") restClient: RestClient,
        props: AuthorizationProperties,
    ): AuthorizationClient {
        val base: AuthorizationClient = RestClientAuthorizationClient(restClient)
        return if (props.cache.enabled) CachingAuthorizationClient(base, props.cache) else base
    }

    @Bean
    @ConditionalOnBean(AuthorizationClient::class)
    @ConditionalOnMissingBean
    fun actorEnrichmentService(client: AuthorizationClient) = ActorEnrichmentService(client)

    @Bean
    @ConditionalOnBean(MeterRegistry::class)
    @ConditionalOnMissingBean
    fun auditMetrics(registry: MeterRegistry) = AuditMetrics(registry)
}
