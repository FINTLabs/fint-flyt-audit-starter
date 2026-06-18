package no.novari.flyt.audit.config

import org.springframework.context.annotation.Import

/**
 * Aktiverer fint-flyt-audit-starter eksplisitt.
 *
 * Med Spring Boot auto-konfigurasjon er dette normalt ikke nødvendig — starteren
 * registreres automatisk via META-INF/spring/AutoConfiguration.imports. Bruk
 * @EnableFlytAuditing for eksplisitt kontroll eller i ikke-Spring-Boot-kontekster.
 *
 * ```
 * @SpringBootApplication
 * @EnableFlytAuditing
 * class MyApplication
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(FlytAuditAutoConfiguration::class)
annotation class EnableFlytAuditing
