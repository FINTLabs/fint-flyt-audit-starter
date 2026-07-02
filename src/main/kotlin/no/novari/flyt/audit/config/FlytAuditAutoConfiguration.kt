package no.novari.flyt.audit.config

import io.micrometer.core.instrument.MeterRegistry
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorAuditorAware
import no.novari.flyt.audit.actor.ActorDisplayProperties
import no.novari.flyt.audit.actor.ActorDisplayResolver
import no.novari.flyt.audit.actor.ActorEnrichmentService
import no.novari.flyt.audit.actor.ActorNameLookup
import no.novari.flyt.audit.actor.HttpActorNameLookup
import no.novari.flyt.audit.actor.NoOpActorNameLookup
import no.novari.flyt.audit.authorization.AuthorizationClient
import no.novari.flyt.audit.authorization.AuthorizationProperties
import no.novari.flyt.audit.authorization.AuthorizationRestClientConfiguration
import no.novari.flyt.audit.authorization.CachingAuthorizationClient
import no.novari.flyt.audit.authorization.RestClientAuthorizationClient
import no.novari.flyt.audit.metrics.AuditMetrics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.web.client.RestClient

@AutoConfiguration
@ConditionalOnClass(AuditingEntityListener::class)
@EnableConfigurationProperties(AuthorizationProperties::class, ActorDisplayProperties::class)
@Import(AuthorizationRestClientConfiguration::class)
class FlytAuditAutoConfiguration {
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
    @ConditionalOnMissingBean(ActorNameLookup::class)
    fun httpActorNameLookup(client: AuthorizationClient): ActorNameLookup = HttpActorNameLookup(client)

    @Bean
    @ConditionalOnMissingBean(ActorNameLookup::class)
    fun noOpActorNameLookup(): ActorNameLookup = NoOpActorNameLookup()

    @Bean
    @ConditionalOnMissingBean
    fun actorEnrichmentService(lookup: ActorNameLookup) = ActorEnrichmentService(lookup)

    @Bean
    @ConditionalOnMissingBean
    fun actorDisplayResolver(
        lookup: ActorNameLookup,
        properties: ActorDisplayProperties,
    ) = ActorDisplayResolver(lookup, properties)

    @Bean
    @ConditionalOnBean(MeterRegistry::class)
    @ConditionalOnMissingBean
    fun auditMetrics(registry: MeterRegistry) = AuditMetrics(registry)
}
