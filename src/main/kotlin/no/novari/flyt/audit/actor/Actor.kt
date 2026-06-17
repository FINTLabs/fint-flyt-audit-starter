package no.novari.flyt.audit.actor

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.UUID

/**
 * Aktør som har utført en endring fanget av endringsloggen.
 *
 * Serialiseres som JSONB. For brukere lagres kun OID (object identifier fra Entra) —
 * persondata som navn og e-post lagres ikke, men hentes ved presentasjons-tid fra
 * `fint-flyt-authorization-service`. Se løsningsanalysen §3.5.6.
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
sealed interface Actor {
    @get:JsonIgnore
    val type: ActorType

    data class User(
        val oid: UUID,
    ) : Actor {
        override val type: ActorType get() = ActorType.USER
    }

    data object System : Actor {
        override val type: ActorType get() = ActorType.SYSTEM
    }

    data class M2M(
        val clientId: String,
    ) : Actor {
        override val type: ActorType get() = ActorType.M2M
    }

    data object Unknown : Actor {
        override val type: ActorType get() = ActorType.UNKNOWN
    }
}
