package no.novari.flyt.audit.history

import no.novari.flyt.audit.actor.Actor
import java.time.Instant

/**
 * Én rad i endringshistorikken for en entitet.
 *
 * @param timestamp når revisjonen ble registrert
 * @param type hva slags endring revisjonen representerer
 * @param actor aktøren som utførte endringen (slik den ble lagret)
 * @param actorDisplay visningsnavn for aktøren, utledet av `ActorDisplayResolver` ved
 *   presentasjons-tid: brukernavn hydrert fra `fint-flyt-authorization-service`, eller
 *   fallback-verdi for System/M2M/Unknown (f.eks. `"System"`). `null` når navnet ikke
 *   kunne hentes, eller når fallback-verdien er konfigurert til `null`.
 * @param snapshot revisjonens tilstand slik `mapSnapshot` eksponerer den. `null` for
 *   slettede revisjoner (DELETED).
 */
data class HistoryEntryDto<T>(
    val timestamp: Instant,
    val type: HistoryEventType,
    val actor: Actor,
    val actorDisplay: String?,
    val snapshot: T?,
)
