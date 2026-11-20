package no.novari.flyt.audit.actor

/**
 * Discriminator for [Actor] subtypes. Matches the `type` field in the JSONB format
 * and is the tag value in Jackson's `@JsonTypeInfo`/`@JsonSubTypes`.
 */
enum class ActorType {
    USER,
    SYSTEM,
    M2M,
    UNKNOWN,
}
