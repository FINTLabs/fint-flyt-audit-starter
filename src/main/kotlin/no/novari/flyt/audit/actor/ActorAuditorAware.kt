package no.novari.flyt.audit.actor

import org.slf4j.LoggerFactory
import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.util.Optional
import java.util.UUID

/**
 * Utleder hvilken [Actor] som skal logges som createdBy/lastModifiedBy.
 *
 * Følger claim-konvensjonen i `flyt-web-resource-server`:
 * - Brukere har `objectidentifier`-claim (UUID, FINT/Novari-ekvivalent til Entras `oid`)
 * - Interne M2M-klienter har bare standard OAuth2 `sub`-claim (ingen `objectidentifier`)
 *
 * Ingen authentication / ikke-JWT principal → [Actor.System] (typisk Kafka- og scheduler-flyt).
 */
class ActorAuditorAware : AuditorAware<Actor> {
    override fun getCurrentAuditor(): Optional<Actor> = Optional.of(resolveActor())

    private fun resolveActor(): Actor {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null) {
            logger.debug("Ingen Authentication i SecurityContext — bruker Actor.System")
            return Actor.System
        }

        val principal = authentication.principal
        if (principal !is Jwt) {
            logger.debug(
                "Authentication.principal er ikke Jwt (faktisk type: {}) — bruker Actor.System",
                principal?.javaClass?.name ?: "null",
            )
            return Actor.System
        }

        val objectIdentifier = principal.getClaimAsString(OBJECT_IDENTIFIER_CLAIM)
        if (objectIdentifier != null) {
            return try {
                Actor.User(UUID.fromString(objectIdentifier))
            } catch (_: IllegalArgumentException) {
                logger.warn(
                    "JWT '{}'-claim er ikke en gyldig UUID: '{}' — bruker Actor.Unknown",
                    OBJECT_IDENTIFIER_CLAIM,
                    objectIdentifier,
                )
                Actor.Unknown
            }
        }

        val subject = principal.subject
        if (subject != null) {
            return Actor.M2M(subject)
        }

        logger.warn(
            "JWT mangler både '{}'- og 'sub'-claim — bruker Actor.Unknown. " +
                "Tilgjengelige claims: {}",
            OBJECT_IDENTIFIER_CLAIM,
            principal.claims.keys,
        )
        return Actor.Unknown
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(ActorAuditorAware::class.java)
        private const val OBJECT_IDENTIFIER_CLAIM = "objectidentifier"
    }
}
