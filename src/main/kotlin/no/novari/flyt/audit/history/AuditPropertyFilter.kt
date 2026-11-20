package no.novari.flyt.audit.history

/**
 * Extra restriction on [EnversHistoryService.findAllHistory]: only revisions where `property`
 * (a field on the audited entity) is among `allowedValues` are returned.
 *
 * Used to scope the `/history` endpoint (the list across all tenants) in
 * [no.novari.flyt.audit.web.HistoryControllerSupport.additionalFilter]. For example, a service whose
 * entity belongs to a source application can limit the list to the source applications the
 * signed-in user has access to.
 *
 * Empty [allowedValues] means "access to no tenants" and gives an empty page
 * ([EnversHistoryService.findAllHistory] then returns without querying the database).
 */
data class AuditPropertyFilter(
    val property: String,
    val allowedValues: Collection<Any>,
)
