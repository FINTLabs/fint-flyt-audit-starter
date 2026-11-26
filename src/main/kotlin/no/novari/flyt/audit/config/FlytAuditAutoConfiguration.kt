package no.novari.flyt.audit.config

import io.micrometer.core.instrument.MeterRegistry
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorAuditorAware
import no.novari.flyt.audit.actor.ActorDisplayProperties
import no.novari.flyt.audit.actor.ActorDisplayResolver
import no.novari.flyt.audit.actor.ActorNameLookup
import no.novari.flyt.audit.actor.HttpActorNameLookup
import no.novari.flyt.audit.actor.NoOpActorNameLookup
import no.novari.flyt.audit.authorization.AuthorizationClient
import no.novari.flyt.audit.authorization.AuthorizationRestClientConfiguration
import no.novari.flyt.audit.metrics.AuditMetrics
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.domain.support.AuditingEntityListener

// After Boot's MeterRegistry, so @ConditionalOnBean(MeterRegistry::class) on auditMetrics sees it.
@AutoConfiguration(
    after = [AuthorizationRestClientConfiguration::class, CompositeMeterRegistryAutoConfiguration::class],
)
@ConditionalOnClass(AuditingEntityListener::class)
@EnableConfigurationProperties(ActorDisplayProperties::class)
class FlytAuditAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = ["flytAuditorAware"])
    fun flytAuditorAware(): AuditorAware<Actor> = ActorAuditorAware()

    /**
     * `@ConditionalOnBean(AuthorizationClient::class)` on two alternative `@Bean` methods depends on
     * the order: it is evaluated in the REGISTER_BEAN phase, which can run before
     * the beans of [AuthorizationRestClientConfiguration] are registered, even with
     * `@AutoConfigureAfter`. One bean with an [ObjectProvider] avoids the problem: the lookup happens
     * lazily, when this method runs, and then creates the AuthorizationClient beans
     * regardless of registration order.
     */
    @Bean
    @ConditionalOnMissingBean(ActorNameLookup::class)
    fun actorNameLookup(authorizationClient: ObjectProvider<AuthorizationClient>): ActorNameLookup {
        val client = authorizationClient.ifAvailable
        if (client != null) {
            logger.info("Registering HttpActorNameLookup, so names are resolved by authorization-service")
            return HttpActorNameLookup(client)
        }
        logger.warn(
            "No AuthorizationClient or custom ActorNameLookup found, " +
                "falling back to NoOpActorNameLookup. createdBy/lastModifiedBy will not be resolved to names.",
        )
        return NoOpActorNameLookup()
    }

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

    /**
     * Without `store_data_at_delete`, Envers stores only `id` in DEL rows. Tenant fields are then `null`
     * and drop out of property-filtered history lookups ([EnversHistoryService.findAllHistory]
     * with [no.novari.flyt.audit.history.AuditPropertyFilter]), so deletions would be missing from
     * a tenant-scoped `/history`. `putIfAbsent` lets the app override it with
     * `spring.jpa.properties.org.hibernate.envers.store_data_at_delete`.
     */
    @Bean
    fun enversStoreDataAtDeleteCustomizer() =
        HibernatePropertiesCustomizer { properties ->
            properties.putIfAbsent("org.hibernate.envers.store_data_at_delete", "true")
        }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(FlytAuditAutoConfiguration::class.java)
    }
}
