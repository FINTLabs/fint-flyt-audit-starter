package no.novari.flyt.audit.actor

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Konfigurerbare visningsverdier for [ActorDisplayResolver].
 *
 * Alle felter kan settes til `null` i `application.yaml` for å propagere `null`
 * i stedet for en fallback-streng:
 * ```yaml
 * novari:
 *   flyt:
 *     audit:
 *       display:
 *         unknown: null    # returnerer null i stedet for "Ukjent"
 * ```
 */
@ConfigurationProperties("novari.flyt.audit.display")
data class ActorDisplayProperties(
    val system: String? = "System",
    val unknown: String? = "Ukjent",
    val unknownUser: String? = null,
    val m2m: String? = null,
)
