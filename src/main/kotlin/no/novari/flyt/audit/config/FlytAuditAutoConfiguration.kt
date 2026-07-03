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
import no.novari.flyt.audit.authorization.AuthorizationRestClientConfiguration
import no.novari.flyt.audit.metrics.AuditMetrics
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@AutoConfiguration
@AutoConfigureAfter(AuthorizationRestClientConfiguration::class)
@ConditionalOnClass(AuditingEntityListener::class)
@EnableConfigurationProperties(ActorDisplayProperties::class)
class FlytAuditAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = ["flytAuditorAware"])
    fun flytAuditorAware(): AuditorAware<Actor> = ActorAuditorAware()

    @Bean
    @ConditionalOnMissingBean
    fun applicationContextHolder() = ApplicationContextHolder()

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
