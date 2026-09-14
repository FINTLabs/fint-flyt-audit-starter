package no.novari.flyt.audit.actor

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.charset.StandardCharsets

/**
 * Kontrakt for propagering av [Actor] i record-headere.
 */
object ActorHeader {
    const val HEADER_NAME = "flyt.actor"

    private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()

    @JvmStatic
    fun toHeaderValue(actor: Actor): ByteArray = toHeaderValueString(actor).toByteArray(StandardCharsets.UTF_8)

    @JvmStatic
    fun toHeaderValueString(actor: Actor): String = mapper.writeValueAsString(actor)

    @JvmStatic
    fun fromHeaderValue(value: ByteArray): Actor = fromHeaderValue(value.toString(StandardCharsets.UTF_8))

    @JvmStatic
    fun fromHeaderValue(value: String): Actor = mapper.readValue(value, Actor::class.java)

    @JvmStatic
    fun fromHeaderValueOrNull(value: ByteArray?): Actor? = value?.let { runCatching { fromHeaderValue(it) }.getOrNull() }

    @JvmStatic
    fun fromHeaderValueOrNull(value: String?): Actor? = value?.let { runCatching { fromHeaderValue(it) }.getOrNull() }
}
