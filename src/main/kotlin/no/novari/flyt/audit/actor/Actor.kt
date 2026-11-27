package no.novari.flyt.audit.actor

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.Serializable
import java.util.UUID

/**
 * The actor that made a change recorded in the change log.
 *
 * Stored as JSONB. For users only the OID (object identifier from Entra) is stored.
 * Personal data such as name and e-mail is not stored, but looked up at presentation time in
 * `fint-flyt-authorization-service`.
 *
 * JSON-format:
 * ```
 * {"type":"USER","oid":"<uuid>"}
 * {"type":"SYSTEM"}
 * {"type":"M2M","clientId":"<azp>"}
 * {"type":"UNKNOWN"}
 * ```
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = Actor.User::class, name = "USER"),
    JsonSubTypes.Type(value = Actor.System::class, name = "SYSTEM"),
    JsonSubTypes.Type(value = Actor.M2M::class, name = "M2M"),
    JsonSubTypes.Type(value = Actor.Unknown::class, name = "UNKNOWN"),
)
sealed interface Actor : Serializable {
    @get:JsonIgnore
    val type: ActorType

    data class User(
        val oid: UUID,
    ) : Actor {
        override val type: ActorType get() = ActorType.USER

        private companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data object System : Actor {
        override val type: ActorType get() = ActorType.SYSTEM

        private fun readResolve(): Any = System

        private const val serialVersionUID: Long = 1L
    }

    data class M2M(
        val clientId: String,
    ) : Actor {
        override val type: ActorType get() = ActorType.M2M

        private companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data object Unknown : Actor {
        override val type: ActorType get() = ActorType.UNKNOWN

        private fun readResolve(): Any = Unknown

        private const val serialVersionUID: Long = 1L
    }
}
