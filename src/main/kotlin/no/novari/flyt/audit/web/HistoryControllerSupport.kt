package no.novari.flyt.audit.web

import no.novari.flyt.audit.history.EnversHistoryService
import no.novari.flyt.audit.history.HistoryFilter
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

/**
 * Abstrakt controller-base som eksponerer endringshistorikk for én entitet.
 *
 * En tjeneste lager en konkret `@RestController` med eget `@RequestMapping`:
 * ```
 * @RestController
 * @RequestMapping("/api/intern/min-tjeneste/ting")
 * class TingHistoryController(service: TingHistoryService)
 *     : HistoryControllerSupport<Ting, Long>(service)
 * ```
 * som da svarer på `GET /api/intern/min-tjeneste/ting/{id}/history`.
 *
 * Navn-hydrering og paginering håndteres i [EnversHistoryService] — denne
 * klassen er bare et tynt REST-lag. Resultater returneres alltid nyeste revisjon
 * først; sortering kan ikke overstyres via request-parametere.
 *
 * **Tilgangskontroll:** starteren legger ingen autentiserings- eller autorisasjonsgating
 * på endepunktet. Konsumenten er ansvarlig for å sikre det — typisk via reverse proxy
 * (f.eks. ved å montere kontrolleren under et internt path-prefix som allerede er
 * autentisert i infrastrukturen) eller via Spring Security.
 */
abstract class HistoryControllerSupport<T : Any, ID : Any>(
    private val historyService: EnversHistoryService<T, ID>,
) {
    @GetMapping("/{id}/history")
    fun history(
        @PathVariable id: ID,
        @PageableDefault(size = 20)
        pageable: Pageable,
        filter: HistoryFilter,
    ): HistoryPageDto<T> = HistoryPageDto.from(historyService.findHistory(id, pageable, filter))

    @GetMapping("/history")
    fun allHistory(
        @PageableDefault(size = 20)
        pageable: Pageable,
        filter: HistoryFilter,
    ): EntityHistoryPageDto<T, ID> = EntityHistoryPageDto.from(historyService.findAllHistory(pageable, filter))
}
