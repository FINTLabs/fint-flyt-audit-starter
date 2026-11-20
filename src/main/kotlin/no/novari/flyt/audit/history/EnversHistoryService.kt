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
 * Generic base service that reads change history from Hibernate Envers.
 *
 * Three type parameters: `T` is the audited entity, `ID` is its key type, and `S`
 * is the shape of `snapshot` in the response. The app implements [mapSnapshot] and so
 * chooses what is exposed: a safe DTO (so lazy relations, internal fields or personal
 * data do not leak into the REST contract), or the raw entity (`S` = `T`):
 * ```
 * // Safe DTO:
 * class MyEntityHistoryService(em: EntityManager, resolver: ActorDisplayResolver)
 *     : EnversHistoryService<MyEntity, Long, MyEntityView>(MyEntity::class.java, em, resolver) {
 *     override fun mapSnapshot(entity: MyEntity) = MyEntityView(entity.id, entity.navn)
 * }
 *
 * // Raw entity (choose S = T on purpose):
 * class MyEntityHistoryService(em: EntityManager, resolver: ActorDisplayResolver)
 *     : EnversHistoryService<MyEntity, Long, MyEntity>(MyEntity::class.java, em, resolver) {
 *     override fun mapSnapshot(entity: MyEntity) = entity
 * }
 * ```
 *
 * Results are always newest revision first (fixed order, cannot be overridden).
 * Actor names are resolved in one batch call per page through [ActorDisplayResolver], to avoid
 * N+1 calls to `fint-flyt-authorization-service`.
 */
abstract class EnversHistoryService<T : Any, ID : Any, S : Any>(
    private val entityClass: Class<T>,
    private val entityManager: EntityManager,
    private val displayResolver: ActorDisplayResolver,
) {
    /**
     * Maps an entity revision to the shape exposed as `snapshot`. Return a safe DTO
     * so the raw JPA entity does not leak, or `entity` (with `S` = `T`) to expose the
     * entity on purpose.
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
        if (propertyFilter != null && propertyFilter.allowedValues.isEmpty()) {
            return PageImpl(emptyList(), pageable, 0)
        }

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

    // Envers returns a partly filled object (only id) for DEL revisions,
    // so a null snapshot is more honest for deleted entries.
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
