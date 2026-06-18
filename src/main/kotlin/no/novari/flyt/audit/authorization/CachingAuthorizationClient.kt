package no.novari.flyt.audit.authorization

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.Optional
import java.util.UUID

/**
 * Caffeine-cachelag rundt [AuthorizationClient] for å begrense kall mot authorization-service.
 *
 * Cache-nøkkel er OID; verdien er `Optional<AuthorizedUserDto>` der tom Optional betyr at OID
 * ikke finnes. TTL og maks størrelse styres av [AuthorizationProperties.Cache].
 * Aktiveres automatisk via `flyt.audit.authorization.cache.enabled=true` (default).
 */
class CachingAuthorizationClient(
    private val delegate: AuthorizationClient,
    props: AuthorizationProperties.Cache,
) : AuthorizationClient {
    // Optional.empty() = OID finnes ikke; Optional.of(dto) = funnet (name kan være null)
    private val cache =
        Caffeine
            .newBuilder()
            .maximumSize(props.maxSize)
            .expireAfterWrite(props.ttl)
            .build<UUID, Optional<AuthorizedUserDto>>()

    override fun findByObjectIdentifier(oid: UUID): AuthorizedUserDto? {
        val cached = cache.getIfPresent(oid)
        if (cached != null) return cached.orElse(null)
        val result = delegate.findByObjectIdentifier(oid)
        cache.put(oid, Optional.ofNullable(result))
        return result
    }

    override fun lookupUsers(oids: List<UUID>): List<AuthorizedUserDto> {
        val distinct = oids.distinct()
        val result = mutableListOf<AuthorizedUserDto>()
        val missing = mutableListOf<UUID>()

        for (oid in distinct) {
            val hit = cache.getIfPresent(oid)
            when {
                hit == null -> missing.add(oid)
                hit.isPresent -> result.add(hit.get())
            }
        }

        if (missing.isEmpty()) return result

        val fetched = delegate.lookupUsers(missing)
        val byOid = fetched.associateBy { it.objectIdentifier }
        for (oid in missing) {
            cache.put(oid, Optional.ofNullable(byOid[oid]))
            byOid[oid]?.let { result.add(it) }
        }

        return result
    }
}
