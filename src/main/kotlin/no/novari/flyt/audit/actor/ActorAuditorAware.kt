package no.novari.flyt.audit.actor

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.util.Optional
import java.util.UUID

/**
 * Decides which [Actor] is recorded as createdBy/lastModifiedBy.
 *
 * Follows the claim convention in `flyt-web-resource-server`:
 * - Users have the `objectidentifier` claim (a UUID, the FINT/Novari equivalent of Entra's `oid`).
 * - Internal M2M clients only have the standard OAuth2 `sub` claim (no `objectidentifier`).
 *
 * No authentication or a non-JWT principal gives [Actor.System] (typically Kafka and scheduled work).
 */
class ActorAuditorAware : AuditorAware<Actor> {
    override fun getCurrentAuditor(): Optional<Actor> = Optional.of(resolveActor())

    private fun resolveActor(): Actor {
        ActorContext.currentActor()?.let { return it }

        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null) {
            logger.debug("No Authentication in the SecurityContext, using Actor.System")
            return Actor.System
        }

        val principal = authentication.principal
        if (principal !is Jwt) {
            logger.debug(
                "Authentication.principal is not a Jwt (actual type: {}), using Actor.System",
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
                    "JWT '{}' claim is not a valid UUID: '{}', using Actor.Unknown",
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
            "JWT has neither a '{}' nor a 'sub' claim, using Actor.Unknown. " +
                "Available claims: {}",
            OBJECT_IDENTIFIER_CLAIM,
            principal.claims.keys,
        )
        return Actor.Unknown
    }

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(ActorAuditorAware::class.java)
        private const val OBJECT_IDENTIFIER_CLAIM = "objectidentifier"
    }
}
