package no.novari.flyt.audit.revision

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorAuditorAware
import no.novari.flyt.audit.config.ApplicationContextHolder
import org.hibernate.envers.RevisionListener

class ActorRevisionListener : RevisionListener {
    override fun newRevision(revisionEntity: Any) {
        val entity = revisionEntity as ActorRevisionEntity
        entity.actor =
            try {
                ApplicationContextHolder
                    .getContext()
                    .getBean(ActorAuditorAware::class.java)
                    .currentAuditor
                    .orElse(Actor.System)
            } catch (_: Exception) {
                Actor.System
            }
    }
}
