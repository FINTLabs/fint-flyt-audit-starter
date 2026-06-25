package no.novari.flyt.audit.config

import jakarta.persistence.EntityManagerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * Aktiverer JPA Auditing for konsumenter som har Spring Data JPA på classpath.
 *
 * Bevisst en separat auto-config-klasse (ikke nested i [FlytAuditAutoConfiguration]):
 * når [EnableFlytAuditing] importerer hovedklassen via @Import, prosesseres det under
 * den "vanlige" config-fasen — FØR EntityManagerFactory-bønnen er registrert. Hadde
 * @EnableJpaAuditing vært nested der, ville @ConditionalOnBean returnert false og
 * auditing aldri aktivert i produksjon.
 *
 * Ved å være en separat @AutoConfiguration som kun registreres via
 * `AutoConfiguration.imports`, kjører den etter auto-config-mekanismen sin ordinære
 * ordering — som med @AutoConfigureAfter(HibernateJpaAutoConfiguration) garanterer
 * at JPA-laget er klart og @ConditionalOnBean(EntityManagerFactory) evalueres riktig.
 *
 * @ConditionalOnMissingBean(name = "jpaAuditingHandler") hindrer konflikt hvis
 * konsumenten har sin egen @EnableJpaAuditing.
 */
@AutoConfiguration(after = [HibernateJpaAutoConfiguration::class])
@ConditionalOnBean(EntityManagerFactory::class)
@ConditionalOnMissingBean(name = ["jpaAuditingHandler"])
@EnableJpaAuditing(auditorAwareRef = "flytAuditorAware")
class FlytAuditJpaAuditingAutoConfiguration
