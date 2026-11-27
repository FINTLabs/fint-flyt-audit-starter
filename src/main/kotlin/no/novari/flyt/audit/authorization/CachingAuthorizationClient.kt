package no.novari.flyt.audit.authorization

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.UUID

/**
 * Caffeine cache around [AuthorizationClient] that limits calls to authorization-service.
 *
 * The key is the OID. A [CachedUser] without a user means the OID does not exist, since Caffeine cannot hold
 * `null`. Time to live and maximum size come from [AuthorizationProperties.Cache].
 * Enabled by `novari.flyt.audit.authorization.cache.enabled=true` (the default).
 */
class CachingAuthorizationClient(
    private val delegate: AuthorizationClient,
    props: AuthorizationProperties.Cache,
) : AuthorizationClient {
    private val cache =
        Caffeine
            .newBuilder()
            .maximumSize(props.maxSize)
            .expireAfterWrite(props.ttl)
            .build<UUID, CachedUser>()

    override fun findByObjectIdentifier(oid: UUID): AuthorizedUserDto? {
        val cached = cache.getIfPresent(oid)
        if (cached != null) return cached.user
        val result = delegate.findByObjectIdentifier(oid)
        cache.put(oid, CachedUser(result))
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
                hit.user != null -> result.add(hit.user)
            }
        }

        if (missing.isEmpty()) return result

        val fetched = delegate.lookupUsers(missing)
        val byOid = fetched.associateBy { it.objectIdentifier }
        for (oid in missing) {
            cache.put(oid, CachedUser(byOid[oid]))
            byOid[oid]?.let { result.add(it) }
        }

        return result
    }
}

private class CachedUser(
    val user: AuthorizedUserDto?,
)
