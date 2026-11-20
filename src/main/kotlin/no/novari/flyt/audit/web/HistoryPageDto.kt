package no.novari.flyt.audit.web

import no.novari.flyt.audit.history.HistoryEntryDto
import org.springframework.data.domain.Page

/**
 * Stable paginated response for the history endpoint.
 *
 * Replaces a raw [Page], whose [org.springframework.data.domain.PageImpl] serialization is not stable, with an explicit
 * JSON contract for consumers.
 */
data class HistoryPageDto<T : Any>(
    val content: List<HistoryEntryDto<T>>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T : Any> from(page: Page<HistoryEntryDto<T>>) =
            HistoryPageDto(
                content = page.content,
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
    }
}
