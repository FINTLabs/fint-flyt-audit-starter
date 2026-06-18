package no.novari.flyt.audit.revision

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.config.ApplicationContextHolder
import org.hibernate.envers.RevisionListener
import org.slf4j.LoggerFactory
import org.springframework.data.domain.AuditorAware

class ActorRevisionListener : RevisionListener {
    override fun newRevision(revisionEntity: Any) {
        val entity = revisionEntity as ActorRevisionEntity
        entity.actor =
            try {
                @Suppress("UNCHECKED_CAST")
                val auditorAware = ApplicationContextHolder.getContext().getBean("flytAuditorAware") as AuditorAware<Actor>
                auditorAware.currentAuditor.orElse(Actor.System)
            } catch (ex: Exception) {
                logger.warn(
                    "Fant ikke bønnen 'flytAuditorAware' — setter actor til System. " +
                        "Sjekk at FlytAuditAutoConfiguration er aktiv.",
                    ex,
                )
                Actor.System
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ActorRevisionListener::class.java)
    }
}
