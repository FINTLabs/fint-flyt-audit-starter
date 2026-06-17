package no.novari.flyt.audit.config

import org.springframework.boot.autoconfigure.AutoConfiguration

/**
 * Auto-konfigurasjon for fint-flyt-audit-starter.
 *
 * Aktiveres automatisk via META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * når starteren er på klassestien. Tjenester aktiverer eksplisitt via @EnableFlytAuditing.
 *
 * Bønne-registreringen fylles inn i FFS-2112.
 */
@AutoConfiguration
class FlytAuditAutoConfiguration
