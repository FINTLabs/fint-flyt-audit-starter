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
    fun `no authentication gives Actor System`() {
        SecurityContextHolder.clearContext()

        assertThat(auditorAware.currentAuditor).contains(Actor.System)
    }

    @Test
    fun `JWT with objectidentifier gives Actor User with the right UUID`() {
        val oid = UUID.randomUUID()
        setJwtAuthentication(claims = mapOf("objectidentifier" to oid.toString()))

        assertThat(auditorAware.currentAuditor).contains(Actor.User(oid))
    }

    @Test
    fun `JWT with an invalid objectidentifier gives Actor Unknown`() {
        setJwtAuthentication(claims = mapOf("objectidentifier" to "ikke-en-uuid"))

        assertThat(auditorAware.currentAuditor).contains(Actor.Unknown)
    }

    @Test
    fun `JWT without objectidentifier but with sub gives Actor M2M`() {
        setJwtAuthentication(claims = mapOf("sub" to "min-klient-id"))

        assertThat(auditorAware.currentAuditor).contains(Actor.M2M("min-klient-id"))
    }

    @Test
    fun `JWT with both objectidentifier and sub gives Actor User (objectidentifier wins)`() {
        val oid = UUID.randomUUID()
        setJwtAuthentication(claims = mapOf("objectidentifier" to oid.toString(), "sub" to "noen-klient"))

        assertThat(auditorAware.currentAuditor).contains(Actor.User(oid))
    }

    @Test
    fun `JWT without objectidentifier and sub gives Actor Unknown`() {
        setJwtAuthentication(claims = mapOf("iss" to "test-issuer"))

        assertThat(auditorAware.currentAuditor).contains(Actor.Unknown)
    }

    @Test
    fun `authentication without a JWT principal gives Actor System`() {
        val auth = TestingAuthenticationToken("ikke-en-jwt", null)
        SecurityContextHolder.getContext().authentication = auth

        assertThat(auditorAware.currentAuditor).contains(Actor.System)
    }

    @Test
    fun `scoped actor wins over the security context`() {
        val jwtOid = UUID.randomUUID()
        val scopedOid = UUID.randomUUID()
        setJwtAuthentication(claims = mapOf("objectidentifier" to jwtOid.toString()))

        ActorContext.withActor(Actor.User(scopedOid)) {
            assertThat(auditorAware.currentAuditor).contains(Actor.User(scopedOid))
        }

        assertThat(auditorAware.currentAuditor).contains(Actor.User(jwtOid))
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
