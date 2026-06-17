package no.novari.flyt.audit.actor

/**
 * Diskriminator-tag for [Actor]-subtyper. Speiler `type`-feltet i JSONB-formatet
 * og brukes som tagverdi i Jacksons `@JsonTypeInfo`/`@JsonSubTypes`.
 */
enum class ActorType {
    USER,
    SYSTEM,
    M2M,
    UNKNOWN,
}
