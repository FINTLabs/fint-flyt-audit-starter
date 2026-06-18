package no.novari.flyt.audit.history

import java.time.Instant

/**
 * Filter for historikk-oppslag. Tom som standard (ingen avgrensning).
 *
 * Foreløpig støttes tidsavgrensning. Controlleren som binder dette fra
 * request-parametere bygges i en senere oppgave (FFS-2110).
 */
data class HistoryFilter(
    val from: Instant? = null,
    val to: Instant? = null,
)
