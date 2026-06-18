package no.novari.flyt.audit.web

import no.novari.flyt.audit.history.EnversHistoryService
import no.novari.flyt.audit.history.HistoryEntryDto
import no.novari.flyt.audit.history.HistoryFilter
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
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
 * klassen er bare et tynt REST-lag.
 */
abstract class HistoryControllerSupport<T : Any, ID : Any>(
    private val historyService: EnversHistoryService<T, ID>,
) {
    @GetMapping("/{id}/history")
    fun history(
        @PathVariable id: ID,
        @PageableDefault(size = 20, sort = ["timestamp"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        filter: HistoryFilter,
    ): Page<HistoryEntryDto<T>> = historyService.findHistory(id, pageable, filter)
}
