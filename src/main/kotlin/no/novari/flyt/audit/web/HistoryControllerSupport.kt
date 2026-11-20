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
 * Abstract controller base that exposes the change history of one entity.
 *
 * A service creates a concrete `@RestController` with its own `@RequestMapping`:
 * ```
 * @RestController
 * @RequestMapping("/api/intern/min-tjeneste/ting")
 * class TingHistoryController(service: TingHistoryService)
 *     : HistoryControllerSupport<Ting, Long, TingView>(service)
 * ```
 * where `TingHistoryService : EnversHistoryService<Ting, Long, TingView>` and `TingView`
 * is the shape of `snapshot` (see [EnversHistoryService.mapSnapshot]).
 * It then answers `GET /api/intern/min-tjeneste/ting/{id}/history` and
 * `GET /api/intern/min-tjeneste/ting/history`.
 *
 * Name resolution and paging are done in [EnversHistoryService], so this
 * class is only a thin REST layer. Results are always newest revision
 * first, and the order cannot be overridden with request parameters.
 *
 * **Access control:** the starter adds no authentication or authorization to the
 * endpoints. The app must secure them, typically by mounting the controller
 * under an internal path prefix secured by the service's own resource server
 * (`no.novari:flyt-web-resource-server`, e.g. `UrlPaths.INTERNAL_API`) or its own Spring
 * Security configuration, **and** with the two hooks below if the entity is scoped per user or tenant
 * (e.g. a source application):
 *
 * - [checkAccess] is called before `/{id}/history` returns data. Throw an exception (e.g. one
 *   mapped to 403) to deny access to a given `id`. No-op by default.
 * - [additionalFilter] is called before `/history` (the list across all tenants) returns data, and lets
 *   the app limit which revisions are visible with an [AuditPropertyFilter] on a
 *   field of the entity (e.g. the source application ID). `null` by default, **so a `/history`
 *   endpoint that does not override it exposes the history of every row, across
 *   all tenants.** Services with tenant-scoped entities must override it.
 *
 * **Requires an authenticated request:** the endpoints take `Authentication` as a non-null
 * parameter, so the request must be authenticated with a Spring Security principal.
 * That holds as long as the controller is mounted behind the service's resource server (above).
 */
abstract class HistoryControllerSupport<T : Any, ID : Any, S : Any>(
    private val historyService: EnversHistoryService<T, ID, S>,
) {
    /** No-op in the base class. Override to enforce access before the history for `id` is returned. */
    protected open fun checkAccess(
        authentication: Authentication,
        id: ID,
    ) {}

    /**
     * `null` in the base class (no restriction). Override to limit `/history`
     * (the list across all tenants) to a subset of revisions, e.g. the user's tenants.
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
