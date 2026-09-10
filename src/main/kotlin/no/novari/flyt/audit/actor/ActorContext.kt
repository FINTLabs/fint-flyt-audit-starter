package no.novari.flyt.audit.actor

/**
 * Trådlokal override for aktør når arbeid utføres på vegne av en bruker uten
 * Spring Security-kontekst, for eksempel i Kafka-konsumenter.
 */
object ActorContext {
    private val currentActor = ThreadLocal<Actor>()

    @JvmStatic
    fun currentActor(): Actor? = currentActor.get()

    @JvmStatic
    fun <T> withActor(
        actor: Actor,
        block: () -> T,
    ): T {
        val previousActor = currentActor.get()
        currentActor.set(actor)
        return try {
            block()
        } finally {
            if (previousActor == null) {
                currentActor.remove()
            } else {
                currentActor.set(previousActor)
            }
        }
    }

    internal fun clear() {
        currentActor.remove()
    }
}
