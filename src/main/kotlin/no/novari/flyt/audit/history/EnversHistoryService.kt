package no.novari.flyt.audit.history

import jakarta.persistence.EntityManager
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorEnrichmentService
import no.novari.flyt.audit.revision.ActorRevisionEntity
import org.hibernate.envers.AuditReaderFactory
import org.hibernate.envers.RevisionType
import org.hibernate.envers.query.AuditEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.Instant

/**
 * Generisk basistjeneste for å lese endringshistorikk fra Hibernate Envers.
 *
 * En tjeneste lager en konkret subklasse per auditert entitet, f.eks.:
 * ```
 * @Service
 * class MyEntityHistoryService(em: EntityManager, enrichment: ActorEnrichmentService)
 *     : EnversHistoryService<MyEntity, Long>(MyEntity::class.java, em, enrichment)
 * ```
 *
 * Resultater returneres alltid nyeste revisjon først (fast sortering, kan ikke overstyres).
 * Aktør-navn hydreres i ett batch-kall per side (se [ActorEnrichmentService])
 * for å unngå N+1 mot `fint-flyt-authorization-service`.
 */
abstract class EnversHistoryService<T : Any, ID : Any>(
    private val entityClass: Class<T>,
    private val entityManager: EntityManager,
    private val enrichmentService: ActorEnrichmentService,
) {
    open fun findHistory(
        id: ID,
        pageable: Pageable,
        filter: HistoryFilter = HistoryFilter(),
    ): Page<HistoryEntryDto<T>> {
        val reader = AuditReaderFactory.get(entityManager)

        val query =
            reader
                .createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(id))

        filter.from?.let {
            query.add(AuditEntity.revisionProperty("revtstmp").ge(it.toEpochMilli()))
        }
        filter.to?.let {
            query.add(AuditEntity.revisionProperty("revtstmp").lt(it.toEpochMilli()))
        }

        query
            .addOrder(AuditEntity.revisionNumber().desc())
            .setFirstResult(pageable.offset.toInt())
            .setMaxResults(pageable.pageSize)

        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>

        val actors = rows.map { (it[1] as ActorRevisionEntity).actor }
        val nameByOid = enrichmentService.enrich(actors)

        val content =
            rows.map { row ->
                val revision = row[1] as ActorRevisionEntity
                val revisionType = row[2] as RevisionType
                val display = (revision.actor as? Actor.User)?.oid?.let { nameByOid[it] }

                // Envers returnerer et delvis utfylt objekt (kun id) for DEL-revisjoner;
                // det er mer ærlig å eksponere snapshot som null for slettede entries.
                @Suppress("UNCHECKED_CAST")
                val snapshot = if (revisionType == RevisionType.DEL) null else row[0] as T?

                HistoryEntryDto(
                    timestamp = Instant.ofEpochMilli(revision.revtstmp),
                    type = HistoryEventType.from(revisionType),
                    actor = revision.actor,
                    actorDisplay = display,
                    snapshot = snapshot,
                )
            }

        return PageImpl(content, pageable, countRevisions(id, filter))
    }

    open fun findAllHistory(
        pageable: Pageable,
        filter: HistoryFilter = HistoryFilter(),
    ): Page<EntityHistoryEntryDto<T, ID>> {
        val reader = AuditReaderFactory.get(entityManager)

        val query =
            reader
                .createQuery()
                .forRevisionsOfEntity(entityClass, false, true)

        filter.from?.let {
            query.add(AuditEntity.revisionProperty("revtstmp").ge(it.toEpochMilli()))
        }
        filter.to?.let {
            query.add(AuditEntity.revisionProperty("revtstmp").lt(it.toEpochMilli()))
        }

        query
            .addOrder(AuditEntity.revisionNumber().desc())
            .setFirstResult(pageable.offset.toInt())
            .setMaxResults(pageable.pageSize)

        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>

        val actors = rows.map { (it[1] as ActorRevisionEntity).actor }
        val nameByOid = enrichmentService.enrich(actors)

        val content =
            rows.map { row ->
                val revision = row[1] as ActorRevisionEntity
                val revisionType = row[2] as RevisionType
                val display = (revision.actor as? Actor.User)?.oid?.let { nameByOid[it] }

                @Suppress("UNCHECKED_CAST")
                val entityId = entityManager.entityManagerFactory.persistenceUnitUtil.getIdentifier(row[0]) as ID

                @Suppress("UNCHECKED_CAST")
                val snapshot = if (revisionType == RevisionType.DEL) null else row[0] as T?

                EntityHistoryEntryDto(
                    entityId = entityId,
                    timestamp = Instant.ofEpochMilli(revision.revtstmp),
                    type = HistoryEventType.from(revisionType),
                    actor = revision.actor,
                    actorDisplay = display,
                    snapshot = snapshot,
                )
            }

        return PageImpl(content, pageable, countAllRevisions(filter))
    }

    private fun countRevisions(
        id: ID,
        filter: HistoryFilter,
    ): Long {
        val reader = AuditReaderFactory.get(entityManager)

        val query =
            reader
                .createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(id))
                .addProjection(AuditEntity.revisionNumber().count())

        filter.from?.let {
            query.add(AuditEntity.revisionProperty("revtstmp").ge(it.toEpochMilli()))
        }
        filter.to?.let {
            query.add(AuditEntity.revisionProperty("revtstmp").lt(it.toEpochMilli()))
        }

        return query.singleResult as Long
    }

    private fun countAllRevisions(filter: HistoryFilter): Long {
        val reader = AuditReaderFactory.get(entityManager)

        val query =
            reader
                .createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .addProjection(AuditEntity.revisionNumber().count())

        filter.from?.let {
            query.add(AuditEntity.revisionProperty("revtstmp").ge(it.toEpochMilli()))
        }
        filter.to?.let {
            query.add(AuditEntity.revisionProperty("revtstmp").lt(it.toEpochMilli()))
        }

        return query.singleResult as Long
    }
}
