package no.novari.flyt.audit.config

import org.springframework.context.annotation.Import

/**
 * Importerer [FlytAuditAutoConfiguration] eksplisitt.
 *
 * I en Spring Boot-app er dette **ikke nødvendig** — alle auto-konfigurasjonene
 * (audit, JPA-auditing og authorization-klient) registreres automatisk via
 * `META-INF/spring/...AutoConfiguration.imports`. Annotasjonen finnes kun som en
 * eksplisitt escape-hatch og importerer bare hovedkonfigurasjonen; JPA-auditing- og
 * authorization-autokonfigurasjonene aktiveres fortsatt via imports-mekanismen.
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
