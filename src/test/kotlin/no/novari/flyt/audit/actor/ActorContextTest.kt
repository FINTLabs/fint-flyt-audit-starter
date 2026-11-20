package no.novari.flyt.audit.actor

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ActorContextTest {
    @Test
    fun `withActor sets the actor only inside the scope`() {
        val actor = Actor.User(UUID.randomUUID())

        assertThat(ActorContext.currentActor()).isNull()
        ActorContext.withActor(actor) {
            assertThat(ActorContext.currentActor()).isEqualTo(actor)
        }
        assertThat(ActorContext.currentActor()).isNull()
    }

    @Test
    fun `withActor restores the outer actor after a nested scope`() {
        val outerActor = Actor.User(UUID.randomUUID())
        val innerActor = Actor.M2M("retry-worker")

        ActorContext.withActor(outerActor) {
            ActorContext.withActor(innerActor) {
                assertThat(ActorContext.currentActor()).isEqualTo(innerActor)
            }

            assertThat(ActorContext.currentActor()).isEqualTo(outerActor)
        }
    }

    @Test
    fun `withActor cleans up when the block throws`() {
        val actor = Actor.User(UUID.randomUUID())

        assertThatThrownBy {
            ActorContext.withActor(actor) {
                throw IllegalStateException("boom")
            }
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(ActorContext.currentActor()).isNull()
    }
}
