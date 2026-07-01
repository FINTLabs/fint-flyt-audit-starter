package no.novari.flyt.audit.actor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ActorDisplayResolverTest {
    private val defaults = ActorDisplayProperties()

    @Test
    fun `User med kjent oid hydreres med navn`() {
        val oid = UUID.randomUUID()
        val resolver = ActorDisplayResolver(ActorNameLookup { mapOf(oid to "Ola Nordmann") }, defaults)

        assertThat(resolver.resolve(Actor.User(oid))).isEqualTo("Ola Nordmann")
    }

    @Test
    fun `User med ukjent oid faller tilbake til unknownUser (default null)`() {
        val resolver = ActorDisplayResolver(ActorNameLookup { emptyMap() }, defaults)

        assertThat(resolver.resolve(Actor.User(UUID.randomUUID()))).isNull()
    }

    @Test
    fun `System bruker default norsk fallback`() {
        val resolver = ActorDisplayResolver(ActorNameLookup { emptyMap() }, defaults)

        assertThat(resolver.resolve(Actor.System)).isEqualTo("System")
    }

    @Test
    fun `Unknown bruker default norsk fallback`() {
        val resolver = ActorDisplayResolver(ActorNameLookup { emptyMap() }, defaults)

        assertThat(resolver.resolve(Actor.Unknown)).isEqualTo("Ukjent")
    }

    @Test
    fun `M2M returnerer clientId når properties m2m er null`() {
        val resolver = ActorDisplayResolver(ActorNameLookup { emptyMap() }, defaults)

        assertThat(resolver.resolve(Actor.M2M("integrasjon-x"))).isEqualTo("integrasjon-x")
    }

    @Test
    fun `M2M kan overstyres via properties`() {
        val props = ActorDisplayProperties(m2m = "Systemintegrasjon")
        val resolver = ActorDisplayResolver(ActorNameLookup { emptyMap() }, props)

        assertThat(resolver.resolve(Actor.M2M("integrasjon-x"))).isEqualTo("Systemintegrasjon")
    }

    @Test
    fun `Unknown-string kan overstyres til null via properties`() {
        val props = ActorDisplayProperties(unknown = null)
        val resolver = ActorDisplayResolver(ActorNameLookup { emptyMap() }, props)

        assertThat(resolver.resolve(Actor.Unknown)).isNull()
    }

    @Test
    fun `null-input propagerer til null`() {
        val resolver = ActorDisplayResolver(ActorNameLookup { emptyMap() }, defaults)

        assertThat(resolver.resolve(null)).isNull()
    }

    @Test
    fun `resolveAll gjør ett batch-kall for alle User-aktører`() {
        val oid1 = UUID.randomUUID()
        val oid2 = UUID.randomUUID()
        var invocations = 0
        var receivedOids: Collection<UUID> = emptyList()
        val lookup =
            ActorNameLookup { oids ->
                invocations++
                receivedOids = oids
                oids.associateWith { "Bruker-$it" }
            }
        val resolver = ActorDisplayResolver(lookup, defaults)

        val result =
            resolver.resolveAll(
                listOf(
                    Actor.User(oid1),
                    Actor.System,
                    Actor.User(oid2),
                    Actor.User(oid1),
                    Actor.Unknown,
                    null,
                ),
            )

        assertThat(invocations).isEqualTo(1)
        assertThat(receivedOids).containsExactlyInAnyOrder(oid1, oid2)
        assertThat(result).containsOnlyKeys(Actor.User(oid1), Actor.User(oid2), Actor.System, Actor.Unknown)
        assertThat(result[Actor.User(oid1)]).isEqualTo("Bruker-$oid1")
        assertThat(result[Actor.System]).isEqualTo("System")
        assertThat(result[Actor.Unknown]).isEqualTo("Ukjent")
    }

    @Test
    fun `resolveAll uten User-aktører gjør ingen lookup`() {
        var invocations = 0
        val lookup =
            ActorNameLookup { oids ->
                invocations++
                oids.associateWith { null }
            }
        val resolver = ActorDisplayResolver(lookup, defaults)

        resolver.resolveAll(listOf(Actor.System, Actor.Unknown))

        assertThat(invocations).isZero()
    }

    @Test
    fun `resolve failsafe når lookup kaster returnerer unknownUser fallback`() {
        val resolver =
            ActorDisplayResolver(
                ActorNameLookup { throw RuntimeException("auth-service nede") },
                ActorDisplayProperties(unknownUser = "Ukjent bruker"),
            )

        assertThat(resolver.resolve(Actor.User(UUID.randomUUID()))).isEqualTo("Ukjent bruker")
    }

    @Test
    fun `resolveAll failsafe når lookup kaster returnerer fallback for User`() {
        val oid = UUID.randomUUID()
        val resolver =
            ActorDisplayResolver(
                ActorNameLookup { throw RuntimeException("nede") },
                ActorDisplayProperties(unknownUser = "Ukjent bruker"),
            )

        val result = resolver.resolveAll(listOf(Actor.User(oid), Actor.System))

        assertThat(result[Actor.User(oid)]).isEqualTo("Ukjent bruker")
        assertThat(result[Actor.System]).isEqualTo("System")
    }
}
