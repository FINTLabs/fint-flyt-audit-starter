package no.novari.flyt.audit.history

import org.hibernate.envers.RevisionType

enum class HistoryEventType {
    CREATED,
    UPDATED,
    DELETED,
    ;

    companion object {
        fun from(revisionType: RevisionType): HistoryEventType =
            when (revisionType) {
                RevisionType.ADD -> CREATED
                RevisionType.MOD -> UPDATED
                RevisionType.DEL -> DELETED
            }
    }
}
