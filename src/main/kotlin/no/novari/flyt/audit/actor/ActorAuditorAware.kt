package no.novari.flyt.audit.actor

import org.slf4j.LoggerFactory
import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.util.Optional
import java.util.UUID

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

        val oid = principal.getClaimAsString("oid")
        if (oid != null) {
            return try {
                Actor.User(UUID.fromString(oid))
            } catch (_: IllegalArgumentException) {
                logger.warn(
                    "JWT 'oid'-claim er ikke en gyldig UUID: '{}' — bruker Actor.Unknown",
                    oid,
                )
                Actor.Unknown
            }
        }

        val azp = principal.getClaimAsString("azp")
        if (azp != null) {
            return Actor.M2M(azp)
        }

        logger.warn(
            "JWT mangler både 'oid' og 'azp'-claim — bruker Actor.Unknown. " +
                "Tilgjengelige claims: {}",
            principal.claims.keys,
        )
        return Actor.Unknown
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(ActorAuditorAware::class.java)
    }
}
