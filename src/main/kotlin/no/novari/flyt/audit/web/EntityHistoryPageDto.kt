package no.novari.flyt.audit.web

import no.novari.flyt.audit.history.EntityHistoryEntryDto
import org.springframework.data.domain.Page

data class EntityHistoryPageDto<T : Any, ID : Any>(
    val content: List<EntityHistoryEntryDto<T, ID>>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T : Any, ID : Any> from(page: Page<EntityHistoryEntryDto<T, ID>>) =
            EntityHistoryPageDto(
                content = page.content,
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
    }
}
