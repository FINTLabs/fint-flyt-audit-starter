package no.novari.flyt.audit.history

import no.novari.flyt.audit.actor.Actor
import java.time.Instant

/**
 * Én rad i endringshistorikken for en entitet.
 *
 * @param timestamp når revisjonen ble registrert
 * @param type hva slags endring revisjonen representerer
 * @param actor aktøren som utførte endringen (slik den ble lagret)
 * @param actorDisplay visningsnavn for aktøren, hydrert fra
 *   `fint-flyt-authorization-service` ved presentasjons-tid. `null` for
 *   ikke-bruker-aktører, eller når navnet ikke kunne hentes.
 * @param snapshot entitetens tilstand i denne revisjonen. `null` for slettede
 *   revisjoner (DELETED).
 */
data class HistoryEntryDto<T>(
    val timestamp: Instant,
    val type: HistoryEventType,
    val actor: Actor,
    val actorDisplay: String?,
    val snapshot: T?,
)
