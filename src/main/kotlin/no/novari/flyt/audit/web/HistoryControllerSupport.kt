package no.novari.flyt.audit.web

import no.novari.flyt.audit.history.AuditPropertyFilter
import no.novari.flyt.audit.history.EnversHistoryService
import no.novari.flyt.audit.history.HistoryFilter
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.Authentication
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
 *     : HistoryControllerSupport<Ting, Long, TingView>(service)
 * ```
 * der `TingHistoryService : EnversHistoryService<Ting, Long, TingView>` og `TingView`
 * er formen på `snapshot` (se [EnversHistoryService.mapSnapshot]).
 * som da svarer på `GET /api/intern/min-tjeneste/ting/{id}/history` og
 * `GET /api/intern/min-tjeneste/ting/history`.
 *
 * Navn-hydrering og paginering håndteres i [EnversHistoryService] — denne
 * klassen er bare et tynt REST-lag. Resultater returneres alltid nyeste revisjon
 * først; sortering kan ikke overstyres via request-parametere.
 *
 * **Tilgangskontroll:** starteren legger ingen autentiserings- eller autorisasjonsgating
 * på endepunktene i seg selv. Konsumenten er ansvarlig for å sikre dem — typisk via reverse
 * proxy (f.eks. ved å montere kontrolleren under et internt path-prefix som allerede er
 * autentisert i infrastrukturen) eller via Spring Security, **og** via de to hookene under
 * dersom entiteten er scopet per bruker/tenant (f.eks. en kildeapplikasjon):
 *
 * - [checkAccess] kalles før `/{id}/history` returnerer data. Kast en exception (f.eks. et
 *   403-mappet unntak) for å nekte tilgang til en gitt `id`. No-op som standard.
 * - [additionalFilter] kalles før `/history` (all-tenant-listen) returnerer data, og lar
 *   konsumenten begrense hvilke revisjoner som er synlige via et [AuditPropertyFilter] på et
 *   felt på entiteten (f.eks. kildeapplikasjons-ID). `null` som standard — **et uoverstyrt
 *   `/history`-endepunkt eksponerer da endringshistorikk for samtlige rader, på tvers av
 *   eventuelle tenant-grenser.** Tjenester med tenant-scopede entiteter må overstyre denne.
 */
abstract class HistoryControllerSupport<T : Any, ID : Any, S : Any>(
    private val historyService: EnversHistoryService<T, ID, S>,
) {
    /** No-op i basisklassen. Override for å håndheve tilgang før historikk for `id` returneres. */
    protected open fun checkAccess(
        authentication: Authentication,
        id: ID,
    ) {}

    /**
     * `null` i basisklassen (ingen restriksjon). Override for å begrense `/history`
     * (all-tenant-listen) til et delsett av revisjoner, f.eks. scopet til brukerens tenant.
     */
    protected open fun additionalFilter(authentication: Authentication): AuditPropertyFilter? = null

    @GetMapping("/{id}/history")
    open fun history(
        authentication: Authentication,
        @PathVariable id: ID,
        @PageableDefault(size = 20)
        pageable: Pageable,
        filter: HistoryFilter,
    ): HistoryPageDto<S> {
        checkAccess(authentication, id)
        return HistoryPageDto.from(historyService.findHistory(id, pageable, filter))
    }

    @GetMapping("/history")
    open fun allHistory(
        authentication: Authentication,
        @PageableDefault(size = 20)
        pageable: Pageable,
        filter: HistoryFilter,
    ): EntityHistoryPageDto<S, ID> =
        EntityHistoryPageDto.from(
            historyService.findAllHistory(pageable, filter, additionalFilter(authentication)),
        )
}
