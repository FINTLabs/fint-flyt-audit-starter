package no.novari.flyt.audit.config

import jakarta.persistence.EntityManagerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * Enables Spring Data JPA auditing with the `flytAuditorAware` bean, after Hibernate's auto-configuration so that
 * `@ConditionalOnBean(EntityManagerFactory)` sees the entity manager factory. Backs off if the app has its own
 * `@EnableJpaAuditing`.
 */
@AutoConfiguration(after = [HibernateJpaAutoConfiguration::class])
@ConditionalOnBean(EntityManagerFactory::class)
@ConditionalOnMissingBean(name = ["jpaAuditingHandler"])
@EnableJpaAuditing(auditorAwareRef = "flytAuditorAware")
class FlytAuditJpaAuditingAutoConfiguration
