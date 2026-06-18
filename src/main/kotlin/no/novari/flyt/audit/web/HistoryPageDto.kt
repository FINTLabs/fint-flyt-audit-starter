package no.novari.flyt.audit.web

import no.novari.flyt.audit.history.HistoryEntryDto
import org.springframework.data.domain.Page

/**
 * Stabil paginert respons for historikk-endepunktet.
 *
 * Erstatter rå [Page] for å unngå Spring Boot 3s ustabile [org.springframework.data.domain.PageImpl]-serialisering
 * og gi en eksplisitt, versjonsstabil JSON-kontrakt for konsumenter.
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
