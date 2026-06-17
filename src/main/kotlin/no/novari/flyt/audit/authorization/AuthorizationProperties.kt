package no.novari.flyt.audit.authorization

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "novari.flyt.audit.authorization")
data class AuthorizationProperties(
    val baseUrl: String = "http://fint-flyt-authorization-service:8080",
    val clientRegistrationId: String = "authorization-service",
    val cache: Cache = Cache(),
) {
    data class Cache(
        val enabled: Boolean = true,
        val ttl: Duration = Duration.ofMinutes(5),
        val maxSize: Long = 10_000,
    )
}
