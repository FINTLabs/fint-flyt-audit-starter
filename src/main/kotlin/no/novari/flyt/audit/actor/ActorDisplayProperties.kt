package no.novari.flyt.audit.actor

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configurable display values for [ActorDisplayResolver].
 *
 * Every field can be set to `null` in `application.yaml` to return `null`
 * instead of a fallback string:
 * ```yaml
 * novari:
 *   flyt:
 *     audit:
 *       display:
 *         unknown: null    # returns null instead of "Ukjent"
 * ```
 */
@ConfigurationProperties("novari.flyt.audit.display")
data class ActorDisplayProperties(
    val system: String? = "System",
    val unknown: String? = "Ukjent",
    val unknownUser: String? = null,
    val m2m: String? = null,
)
