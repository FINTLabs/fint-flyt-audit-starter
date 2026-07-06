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
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository

@AutoConfiguration
@AutoConfigureAfter(AuthorizationRestClientConfiguration::class)
@ConditionalOnClass(AuditingEntityListener::class)
@EnableConfigurationProperties(ActorDisplayProperties::class)
class FlytAuditAutoConfiguration {
    @Bean
    @ConditionalOnClass(ClientRegistrationRepository::class)
    fun flytAuditOauth2ClientDiagnostics(
        environment: Environment,
        clientRegistrationRepositories: ObjectProvider<ClientRegistrationRepository>,
    ): FlytAuditOauth2ClientDiagnostics {
        val clientId = environment.getProperty("spring.security.oauth2.client.registration.authorization-service.client-id")
        val provider = environment.getProperty("spring.security.oauth2.client.registration.authorization-service.provider")
        val tokenUri = environment.getProperty("spring.security.oauth2.client.provider.fint-idp.token-uri")
        val repository = clientRegistrationRepositories.ifAvailable
        logger.info(
            "OAuth2-klient-diagnostikk: registration.client-id={} (lengde={}), " +
                "registration.provider={}, provider.fint-idp.token-uri={}, ClientRegistrationRepository={}",
            if (clientId == null) "MANGLER" else "satt",
            clientId?.length ?: 0,
            provider ?: "MANGLER",
            tokenUri ?: "MANGLER",
            repository?.javaClass?.name ?: "INGEN BØNNE",
        )
        return FlytAuditOauth2ClientDiagnostics()
    }

    @Bean
    @ConditionalOnMissingBean(name = ["flytAuditorAware"])
    fun flytAuditorAware(): AuditorAware<Actor> = ActorAuditorAware()

    @Bean
    @ConditionalOnMissingBean
    fun applicationContextHolder() = ApplicationContextHolder()

    @Bean
    @ConditionalOnBean(AuthorizationClient::class)
    @ConditionalOnMissingBean(ActorNameLookup::class)
    fun httpActorNameLookup(client: AuthorizationClient): ActorNameLookup {
        logger.info("Registrerer HttpActorNameLookup — navnehydrering mot authorization-service er aktiv")
        return HttpActorNameLookup(client)
    }

    @Bean
    @ConditionalOnMissingBean(ActorNameLookup::class)
    fun noOpActorNameLookup(): ActorNameLookup {
        logger.warn(
            "Ingen AuthorizationClient eller egendefinert ActorNameLookup funnet — " +
                "faller tilbake til NoOpActorNameLookup. createdBy/lastModifiedBy vil ikke hydreres til navn.",
        )
        return NoOpActorNameLookup()
    }

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

    private companion object {
        val logger = LoggerFactory.getLogger(FlytAuditAutoConfiguration::class.java)
    }
}

/** Tom markørklasse — selve verdien av bønnen er loggingen i factory-metoden. */
class FlytAuditOauth2ClientDiagnostics
