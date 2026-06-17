package no.novari.flyt.audit.entity

import no.novari.flyt.audit.actor.ActorAuditorAware
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@TestConfiguration
@EnableJpaAuditing(auditorAwareRef = "actorAuditorAware")
class AuditTestConfig {
    @Bean
    fun actorAuditorAware() = ActorAuditorAware()
}
