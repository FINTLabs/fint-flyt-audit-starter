package no.novari.flyt.audit.history

/**
 * Ekstra restriksjon på [EnversHistoryService.findAllHistory]: kun revisjoner der `property`
 * (et felt på den auditerte entiteten) er blant `allowedValues` returneres.
 *
 * Brukes til å tenant-scope `/history`-endepunktet (all-tenant-listen) i
 * [no.novari.flyt.audit.web.HistoryControllerSupport.additionalFilter] — f.eks. en tjeneste der
 * entiteten tilhører en kildeapplikasjon kan begrense listen til kildeapplikasjonene den
 * innloggede brukeren har tilgang til.
 *
 * Tom [allowedValues] betyr «tilgang til ingen tenants» og gir en tom resultatside
 * ([EnversHistoryService.findAllHistory] kortslutter da uten å treffe databasen).
 */
data class AuditPropertyFilter(
    val property: String,
    val allowedValues: Collection<Any>,
)
