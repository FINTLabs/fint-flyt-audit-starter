package no.novari.flyt.audit.revision

import no.novari.flyt.audit.actor.Actor
import org.hibernate.envers.RevisionListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.AuditorAware
import kotlin.jvm.optionals.getOrNull

/**
 * Hibernate Envers [RevisionListener] that sets [ActorRevisionEntity.actor] for every revision.
 *
 * Hibernate creates the listener through Spring's bean container, so the `flytAuditorAware` bean that JPA auditing
 * uses is injected, and both record the same actor.
 */
class ActorRevisionListener(
    @param:Qualifier("flytAuditorAware") private val auditorAware: ObjectProvider<AuditorAware<Actor>>,
) : RevisionListener {
    override fun newRevision(revisionEntity: Any) {
        val auditor = auditorAware.ifAvailable
        if (auditor == null) {
            logger.warn("No 'flytAuditorAware' bean, so the revision actor is System. Check that FlytAuditAutoConfiguration is active.")
        }
        (revisionEntity as ActorRevisionEntity).actor = auditor?.currentAuditor?.getOrNull() ?: Actor.System
    }

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(ActorRevisionListener::class.java)
    }
}
