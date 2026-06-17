package no.novari.flyt.audit.actor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.util.UUID

class ActorAuditorAwareTest {
    private val auditorAware = ActorAuditorAware()

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `ingen authentication gir Actor-System`() {
        SecurityContextHolder.clearContext()

        assertThat(auditorAware.currentAuditor).contains(Actor.System)
    }

    @Test
    fun `JWT med oid gir Actor-User med riktig UUID`() {
        val oid = UUID.randomUUID()
        setJwtAuthentication(mapOf("oid" to oid.toString()))

        assertThat(auditorAware.currentAuditor).contains(Actor.User(oid))
    }

    @Test
    fun `JWT med ugyldig oid gir Actor-Unknown`() {
        setJwtAuthentication(mapOf("oid" to "ikke-en-uuid"))

        assertThat(auditorAware.currentAuditor).contains(Actor.Unknown)
    }

    @Test
    fun `JWT uten oid men med azp gir Actor-M2M`() {
        setJwtAuthentication(mapOf("azp" to "min-klient-id"))

        assertThat(auditorAware.currentAuditor).contains(Actor.M2M("min-klient-id"))
    }

    @Test
    fun `JWT uten oid og azp gir Actor-Unknown`() {
        setJwtAuthentication(mapOf("sub" to "noen"))

        assertThat(auditorAware.currentAuditor).contains(Actor.Unknown)
    }

    @Test
    fun `authentication uten JWT-principal gir Actor-System`() {
        val auth = TestingAuthenticationToken("ikke-en-jwt", null)
        SecurityContextHolder.getContext().authentication = auth

        assertThat(auditorAware.currentAuditor).contains(Actor.System)
    }

    private fun setJwtAuthentication(claims: Map<String, Any>) {
        val jwt =
            Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                mapOf("alg" to "RS256"),
                claims,
            )
        val auth = TestingAuthenticationToken(jwt, null)
        SecurityContextHolder.getContext().authentication = auth
    }
}
