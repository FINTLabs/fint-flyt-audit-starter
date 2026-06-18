package no.novari.flyt.audit.history

import org.springframework.format.annotation.DateTimeFormat
import java.time.Instant

/**
 * Filter for historikk-oppslag. Tom som standard (ingen avgrensning).
 *
 * Bindes fra request-parametere av [no.novari.flyt.audit.web.HistoryControllerSupport]:
 * `?from=2026-06-17T10:00:00Z&to=2026-06-18T10:00:00Z`. Begge er valgfrie.
 */
data class HistoryFilter(
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val from: Instant? = null,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val to: Instant? = null,
)
