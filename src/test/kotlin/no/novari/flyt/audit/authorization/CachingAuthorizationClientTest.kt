package no.novari.flyt.audit.authorization

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Duration
import java.util.UUID

class CachingAuthorizationClientTest {
    private lateinit var delegate: AuthorizationClient
    private lateinit var client: CachingAuthorizationClient

    private val props =
        AuthorizationProperties.Cache(
            enabled = true,
            ttl = Duration.ofMinutes(5),
            maxSize = 100,
        )

    @BeforeEach
    fun setUp() {
        delegate = mock(AuthorizationClient::class.java)
        client = CachingAuthorizationClient(delegate, props)
    }

    @Test
    fun `findByObjectIdentifier - andre kall treffer cache og kaller ikke delegate`() {
        val oid = UUID.randomUUID()
        val dto = AuthorizedUserDto(oid, "Ola Nordmann")
        `when`(delegate.findByObjectIdentifier(oid)).thenReturn(dto)

        val first = client.findByObjectIdentifier(oid)
        val second = client.findByObjectIdentifier(oid)

        assertThat(first).isEqualTo(dto)
        assertThat(second).isEqualTo(dto)
        verify(delegate, times(1)).findByObjectIdentifier(oid)
    }

    @Test
    fun `findByObjectIdentifier - negativt treff caches og kaller ikke delegate på nytt`() {
        val oid = UUID.randomUUID()
        `when`(delegate.findByObjectIdentifier(oid)).thenReturn(null)

        val first = client.findByObjectIdentifier(oid)
        val second = client.findByObjectIdentifier(oid)

        assertThat(first).isNull()
        assertThat(second).isNull()
        verify(delegate, times(1)).findByObjectIdentifier(oid)
    }

    @Test
    fun `findByObjectIdentifier - bruker med null-navn caches korrekt og returneres som funnet`() {
        val oid = UUID.randomUUID()
        val dto = AuthorizedUserDto(oid, null)
        `when`(delegate.findByObjectIdentifier(oid)).thenReturn(dto)

        val first = client.findByObjectIdentifier(oid)
        val second = client.findByObjectIdentifier(oid)

        assertThat(first).isEqualTo(dto)
        assertThat(second).isEqualTo(dto)
        verify(delegate, times(1)).findByObjectIdentifier(oid)
    }

    @Test
    fun `lookupUsers - OIDer i cache slippes ikke gjennom til delegate`() {
        val oid1 = UUID.randomUUID()
        val oid2 = UUID.randomUUID()
        val dto1 = AuthorizedUserDto(oid1, "Ola")
        val dto2 = AuthorizedUserDto(oid2, "Kari")

        `when`(delegate.lookupUsers(listOf(oid1, oid2))).thenReturn(listOf(dto1, dto2))
        client.lookupUsers(listOf(oid1, oid2))

        `when`(delegate.lookupUsers(listOf(oid2))).thenReturn(listOf(dto2))
        val result = client.lookupUsers(listOf(oid1, oid2))

        assertThat(result).containsExactlyInAnyOrder(dto1, dto2)
        verify(delegate, times(1)).lookupUsers(listOf(oid1, oid2))
        verify(delegate, times(0)).lookupUsers(listOf(oid1))
    }

    @Test
    fun `lookupUsers - duplikate OIDer i inndata dedupliseres`() {
        val oid = UUID.randomUUID()
        val dto = AuthorizedUserDto(oid, "Ola")
        `when`(delegate.lookupUsers(listOf(oid))).thenReturn(listOf(dto))

        val result = client.lookupUsers(listOf(oid, oid, oid))

        assertThat(result).containsExactly(dto)
        verify(delegate, times(1)).lookupUsers(listOf(oid))
    }

    @Test
    fun `lookupUsers - cache deles med findByObjectIdentifier`() {
        val oid = UUID.randomUUID()
        val dto = AuthorizedUserDto(oid, "Ola")
        `when`(delegate.findByObjectIdentifier(oid)).thenReturn(dto)

        client.findByObjectIdentifier(oid)
        val result = client.lookupUsers(listOf(oid))

        assertThat(result).containsExactly(dto)
        verify(delegate, times(0)).lookupUsers(any())
    }

    private fun <T> any(): T {
        org.mockito.Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }
}
