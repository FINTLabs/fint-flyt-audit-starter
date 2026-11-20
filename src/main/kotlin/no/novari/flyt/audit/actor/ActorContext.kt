package no.novari.flyt.audit.actor

/**
 * Actor override for work done on behalf of someone without a Spring Security context, for example in Kafka
 * listeners. The actor is bound with a [ScopedValue], so it is visible only inside [withActor], on the same thread.
 *
 * JPA auditing reads the actor when the entity is persisted. Envers creates its revision when the transaction is
 * flushed, so for `@Audited` entities the flush must happen inside [withActor], or the revision gets [Actor.System].
 */
object ActorContext {
    private val currentActor: ScopedValue<Actor> = ScopedValue.newInstance()

    @JvmStatic
    fun currentActor(): Actor? = if (currentActor.isBound) currentActor.get() else null

    @JvmStatic
    fun <T> withActor(
        actor: Actor,
        block: () -> T,
    ): T = ScopedValue.where(currentActor, actor).call(ScopedValue.CallableOp<T, RuntimeException> { block() })
}
