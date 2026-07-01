package no.novari.flyt.audit.actor

import no.novari.flyt.audit.entity.AuditedEntity
import no.novari.flyt.audit.entity.CreatedAuditedEntity
import java.time.Instant

/**
 * Standard DTO-representasjon av audit-felter for REST-responser.
 *
 * `createdBy`/`lastModifiedBy` er hydrerte strenger (navn på bruker, "System",
 * "Ukjent" eller M2M-clientId). `createdByActor`/`lastModifiedByActor` er
 * den strukturerte aktør-typen for klienter som trenger å filtrere/gruppere
 * på oid.
 *
 * Tjenester som vil bygge sin egen DTO-form kan bruke
 * [ActorDisplayResolver.resolve]/[ActorDisplayResolver.resolveAll] direkte
 * i stedet — denne klassen er kun et tilbud.
 */
data class AuditFieldsDto(
    val createdAt: Instant?,
    val createdBy: String?,
    val createdByActor: Actor?,
    val lastModifiedAt: Instant?,
    val lastModifiedBy: String?,
    val lastModifiedByActor: Actor?,
)

fun ActorDisplayResolver.auditFieldsOf(entity: CreatedAuditedEntity): AuditFieldsDto {
    val createdBy = entity.createdBy
    val lastModifiedAt = (entity as? AuditedEntity)?.lastModifiedAt
    val lastModifiedBy = (entity as? AuditedEntity)?.lastModifiedBy
    return AuditFieldsDto(
        createdAt = entity.createdAt,
        createdBy = resolve(createdBy),
        createdByActor = createdBy,
        lastModifiedAt = lastModifiedAt,
        lastModifiedBy = resolve(lastModifiedBy),
        lastModifiedByActor = lastModifiedBy,
    )
}
