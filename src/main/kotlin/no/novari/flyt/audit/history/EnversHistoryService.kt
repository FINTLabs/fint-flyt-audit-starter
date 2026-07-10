package no.novari.flyt.audit.history

import jakarta.persistence.EntityManager
import no.novari.flyt.audit.actor.ActorDisplayResolver
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
 * Tre type-parametere: `T` er den auditerte entiteten, `ID` er nøkkeltypen, og `S`
 * er formen på `snapshot` i responsen. Konsumenten må implementere [mapSnapshot] og velger
 * dermed bevisst hva som eksponeres — enten en trygg DTO (unngår at lazy-relasjoner, interne
 * felt eller PII lekker rått i REST-kontrakten), eller den rå entiteten (`S` = `T`):
 * ```
 * // Trygg DTO:
 * class MyEntityHistoryService(em: EntityManager, resolver: ActorDisplayResolver)
 *     : EnversHistoryService<MyEntity, Long, MyEntityView>(MyEntity::class.java, em, resolver) {
 *     override fun mapSnapshot(entity: MyEntity) = MyEntityView(entity.id, entity.navn)
 * }
 *
 * // Rå entitet (velg S = T bevisst):
 * class MyEntityHistoryService(em: EntityManager, resolver: ActorDisplayResolver)
 *     : EnversHistoryService<MyEntity, Long, MyEntity>(MyEntity::class.java, em, resolver) {
 *     override fun mapSnapshot(entity: MyEntity) = entity
 * }
 * ```
 *
 * Resultater returneres alltid nyeste revisjon først (fast sortering, kan ikke overstyres).
 * Aktør-navn hydreres i ett batch-kall per side via [ActorDisplayResolver] for å unngå
 * N+1 mot `fint-flyt-authorization-service`.
 */
abstract class EnversHistoryService<T : Any, ID : Any, S : Any>(
    private val entityClass: Class<T>,
    private val entityManager: EntityManager,
    private val displayResolver: ActorDisplayResolver,
) {
    /**
     * Mapper en entitets-revisjon til formen som eksponeres i `snapshot`. Returner en trygg
     * DTO for å unngå å lekke rå JPA-entitet, eller `entity` (med `S` = `T`) for å eksponere
     * entiteten bevisst.
     */
    protected abstract fun mapSnapshot(entity: T): S?

    open fun findHistory(
        id: ID,
        pageable: Pageable,
        filter: HistoryFilter = HistoryFilter(),
    ): Page<HistoryEntryDto<S>> {
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

        val displays = displayResolver.resolveAll(rows.map { (it[1] as ActorRevisionEntity).actor })

        val content =
            rows.map { row ->
                val revision = row[1] as ActorRevisionEntity
                val revisionType = row[2] as RevisionType

                HistoryEntryDto(
                    timestamp = Instant.ofEpochMilli(revision.revtstmp),
                    type = HistoryEventType.from(revisionType),
                    actor = revision.actor,
                    actorDisplay = displays[revision.actor],
                    snapshot = snapshotOf(row, revisionType),
                )
            }

        return PageImpl(content, pageable, countRevisions(id, filter))
    }

    open fun findAllHistory(
        pageable: Pageable,
        filter: HistoryFilter = HistoryFilter(),
        propertyFilter: AuditPropertyFilter? = null,
    ): Page<EntityHistoryEntryDto<S, ID>> {
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
        propertyFilter?.let {
            query.add(AuditEntity.property(it.property).`in`(it.allowedValues))
        }

        query
            .addOrder(AuditEntity.revisionNumber().desc())
            .setFirstResult(pageable.offset.toInt())
            .setMaxResults(pageable.pageSize)

        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>

        val displays = displayResolver.resolveAll(rows.map { (it[1] as ActorRevisionEntity).actor })

        val content =
            rows.map { row ->
                val revision = row[1] as ActorRevisionEntity
                val revisionType = row[2] as RevisionType

                @Suppress("UNCHECKED_CAST")
                val entityId = entityManager.entityManagerFactory.persistenceUnitUtil.getIdentifier(row[0]) as ID

                EntityHistoryEntryDto(
                    entityId = entityId,
                    timestamp = Instant.ofEpochMilli(revision.revtstmp),
                    type = HistoryEventType.from(revisionType),
                    actor = revision.actor,
                    actorDisplay = displays[revision.actor],
                    snapshot = snapshotOf(row, revisionType),
                )
            }

        return PageImpl(content, pageable, countAllRevisions(filter, propertyFilter))
    }

    // Envers returnerer et delvis utfylt objekt (kun id) for DEL-revisjoner;
    // det er mer ærlig å eksponere snapshot som null for slettede entries.
    private fun snapshotOf(
        row: Array<Any?>,
        revisionType: RevisionType,
    ): S? {
        if (revisionType == RevisionType.DEL) return null

        @Suppress("UNCHECKED_CAST")
        return mapSnapshot(row[0] as T)
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

    private fun countAllRevisions(
        filter: HistoryFilter,
        propertyFilter: AuditPropertyFilter?,
    ): Long {
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
        propertyFilter?.let {
            query.add(AuditEntity.property(it.property).`in`(it.allowedValues))
        }

        return query.singleResult as Long
    }
}
