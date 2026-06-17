package no.novari.flyt.audit.actor

import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.util.Optional
import java.util.UUID

class ActorAuditorAware : AuditorAware<Actor> {
    override fun getCurrentAuditor(): Optional<Actor> = Optional.of(resolveActor())

    private fun resolveActor(): Actor {
        val authentication =
            SecurityContextHolder.getContext().authentication
                ?: return Actor.System

        val jwt =
            authentication.principal as? Jwt
                ?: return Actor.System

        val oid = jwt.getClaimAsString("oid")
        if (oid != null) {
            return try {
                Actor.User(UUID.fromString(oid))
            } catch (_: IllegalArgumentException) {
                Actor.Unknown
            }
        }

        val azp = jwt.getClaimAsString("azp")
        if (azp != null) {
            return Actor.M2M(azp)
        }

        return Actor.Unknown
    }
}
