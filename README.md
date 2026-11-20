# fint-flyt-audit-starter

Spring Boot starter for change logs in FLYT services. It provides the actor model, a `@MappedSuperclass` hierarchy,
Hibernate Envers integration and support for a history API, so services can add change logs without building the
infrastructure again.

## Contents

- [`Actor`](src/main/kotlin/no/novari/flyt/audit/actor/Actor.kt): sealed interface with the subtypes `User`, `System`,
  `M2M` and `Unknown`. Stored as JSONB.
- [`ActorAuditorAware`](src/main/kotlin/no/novari/flyt/audit/actor/ActorAuditorAware.kt): takes the actor from
  [`ActorContext`](src/main/kotlin/no/novari/flyt/audit/actor/ActorContext.kt), then from the JWT in the security
  context (`objectidentifier` gives `User`, `sub` gives `M2M`, no JWT gives `System`).
- [`ActorContext`](src/main/kotlin/no/novari/flyt/audit/actor/ActorContext.kt): actor override for work done on behalf
  of a user, bound with a `ScopedValue`.
- [`ActorHeader`](src/main/kotlin/no/novari/flyt/audit/actor/ActorHeader.kt): contract and codec for the actor in the
  record header `flyt.actor`.
- [`CreatedAuditedEntity`](src/main/kotlin/no/novari/flyt/audit/entity/CreatedAuditedEntity.kt): `@MappedSuperclass`
  with `createdAt` and `createdBy` (variant B).
- [`AuditedEntity`](src/main/kotlin/no/novari/flyt/audit/entity/AuditedEntity.kt): extends `CreatedAuditedEntity` with
  `lastModifiedAt` and `lastModifiedBy` (variants C, D and E).
- [`ActorRevisionEntity`](src/main/kotlin/no/novari/flyt/audit/revision/ActorRevisionEntity.kt): Envers
  `@RevisionEntity` with a JSONB `actor` column.
- [`AuthorizationClient`](src/main/kotlin/no/novari/flyt/audit/authorization/AuthorizationClient.kt): client for
  `fint-flyt-authorization-service` that looks up names at presentation time.
- A Flyway migration template in [`src/main/resources/flyt-audit-templates/`](src/main/resources/flyt-audit-templates/),
  outside `db/migration/` on purpose, so Flyway in the app does not pick it up (see below).

The architecture is described in [docs/architecture.md](docs/architecture.md).

## Dependency

```kotlin
dependencies {
    implementation("no.novari:flyt-audit-starter:<version>")
}
```

Published to `https://repo.fintlabs.no/releases`. Requires Spring Boot 4 and JDK 25 or newer.

## Getting started

### 1. Auditing is auto-configured

The auto-configurations enable JPA auditing, Envers integration and name resolution as soon as the starter is on the
classpath. No annotation is needed.

### 2. Choose a variant

| Variant | Base class                   | Gives you                                            |
| ------- | ---------------------------- | ---------------------------------------------------- |
| B       | `CreatedAuditedEntity`       | `createdAt` and `createdBy` (creation only)          |
| C       | `AuditedEntity`              | also `lastModifiedAt` and `lastModifiedBy`           |
| D       | `AuditedEntity` + `@Audited` | also full Envers history in an `_aud` table          |
| E       | Variant D + history API      | also a REST endpoint with `HistoryControllerSupport` |

```kotlin
@Entity
@Audited          // only for variants D and E
@Table(name = "my_entity")
class MyEntity : AuditedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var name: String = ""
}
```

### 3. Flyway migrations

The starter provides [`V1__revinfo.sql`](src/main/resources/flyt-audit-templates/V1__revinfo.sql) (the Envers `revinfo`
table and sequence), **only for variants D and E** (Envers history). It is in `flyt-audit-templates/` and **not** in
`db/migration/`, so Flyway in the app does not find it on its own (it would clash with the service's own `V1`). Copy it
to the service's `src/main/resources/db/migration/` and change the version number to fit the service's migrations.

Variants B and C do not need `revinfo`, only the audit columns on the entity table (see below).

The service writes the migrations for the audit columns and the `_aud` table itself.

**Example: audit columns on a new table (variants C, D and E):**

```sql
ALTER TABLE my_entity
    ADD COLUMN created_at       TIMESTAMPTZ NULL,
    ADD COLUMN created_by       JSONB NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb,
    ADD COLUMN last_modified_at TIMESTAMPTZ NULL,
    ADD COLUMN last_modified_by JSONB NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb;
```

The `*_at` columns are nullable. Spring Data sets them on the first insert of new rows.

**Existing tables:** if the table already has a `created_at` field with another name or type, copy the values instead
of setting them to `now()`. It is more correct to leave the field `NULL` for rows created before auditing was added. A
`TIMESTAMP → TIMESTAMPTZ` conversion is safe in place:

```sql
ALTER TABLE my_entity
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
```

For `*_by` columns that already hold strings (names, e-mail, OID): a UUID can be converted to the `Actor.User` format,
anything else becomes `SYSTEM` or `UNKNOWN`:

```sql
ALTER TABLE my_entity
    ALTER COLUMN created_by TYPE JSONB
    USING CASE
        WHEN created_by ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            THEN jsonb_build_object('type', 'USER', 'oid', created_by)
        WHEN lower(created_by) IN ('system', '')
            THEN '{"type":"SYSTEM"}'::jsonb
        ELSE '{"type":"UNKNOWN"}'::jsonb
    END;
```

**Example: `_aud` table (variants D and E):**

```sql
CREATE TABLE my_entity_aud (
    id      BIGINT   NOT NULL,
    rev     BIGINT   NOT NULL REFERENCES revinfo(rev),
    revtype SMALLINT,
    name    VARCHAR(255),
    PRIMARY KEY (id, rev)
);
```

Set `spring.jpa.hibernate.ddl-auto: validate` in production.

**The history endpoints** (`GET /{id}/history` and `GET /history`) support `page`, `size` and `from`/`to` filters. The
order is fixed: the newest revision is always first, and request parameters cannot change it.

> **Access control:** the starter adds no authentication or authorization to the history endpoints. The app must
> secure them, typically by mounting the controller under an internal path prefix secured by the service's own
> resource server (`no.novari:flyt-web-resource-server`), e.g. `no.novari.flyt.webresourceserver.UrlPaths.INTERNAL_API`
> (which requires a valid user JWT and the `USER` role), or with its own Spring Security configuration.

## Actor in asynchronous consumers

Kafka listeners, schedulers and other asynchronous work normally run without a Spring Security context, so
`ActorAuditorAware` falls back to `Actor.System`. When a consumer handles a message on behalf of a user, it must set the
actor explicitly, around exactly the persistence that is audited.

The producer writes the actor to the record header `flyt.actor`:

```kotlin
val actor = auditorAware.currentAuditor.orElse(Actor.System)
val actorHeaderValue = ActorHeader.toHeaderValue(actor)
```

The consumer reads the header and saves inside the actor scope:

```kotlin
val actor =
    record.headers()
        .lastHeader(ActorHeader.HEADER_NAME)
        ?.value()
        ?.let { ActorHeader.fromHeaderValueOrNull(it) }

if (actor != null) {
    ActorContext.withActor(actor) {
        eventRepository.save(event)
    }
} else {
    eventRepository.save(event)
}
```

`ActorContext.withActor(...)` binds the actor with a `ScopedValue`, so it is visible only inside the block, on the same
thread, also if the block throws. The header value uses the same JSON contract as the `Actor` JSONB, for example
`{"type":"USER","oid":"..."}`. Use the header only for traceability, never for authorization.

> **Envers:** JPA auditing reads the actor when the entity is persisted, but Envers creates its revision when the
> transaction commits, also after `saveAndFlush`. For `@Audited` entities, `withActor` must wrap the whole transaction,
> including the commit. Otherwise `createdBy` gets the right user, but the revision in `revinfo` gets `Actor.System`:
>
> ```kotlin
> // Right: the commit happens inside withActor.
> ActorContext.withActor(actor) {
>     myTransactionalService.save(entity)   // @Transactional
> }
>
> // Wrong for @Audited entities: the transaction commits after withActor has ended.
> @Transactional
> fun save(entity: MyEntity, actor: Actor) {
>     ActorContext.withActor(actor) { repository.saveAndFlush(entity) }
> }
> ```

### Authorization per entity or tenant

If the entity is scoped to a user or tenant (e.g. a source application ID), authentication on the path prefix is not
enough. `HistoryControllerSupport` has two hooks for this:

```kotlin
@RestController
@RequestMapping("/api/intern/min-tjeneste/ting")
class TingHistoryController(
    private val tingRepository: TingRepository,
    private val userAuthorizationService: UserAuthorizationService,
    historyService: TingHistoryService,
) : HistoryControllerSupport<Ting, Long, TingView>(historyService) {

    // Called before GET /{id}/history returns data. Throw to deny access.
    override fun checkAccess(authentication: Authentication, id: Long) {
        val ting = tingRepository.findById(id).orElseThrow { TingNotFoundException(id) }
        userAuthorizationService.checkIfUserHasAccessToSourceApplication(authentication, ting.eierId)
    }

    // Called before GET /history (the list across all tenants) returns data.
    override fun additionalFilter(authentication: Authentication): AuditPropertyFilter =
        AuditPropertyFilter(
            property = "eierId",
            allowedValues = userAuthorizationService.getUserAuthorizedSourceApplicationIds(authentication),
        )
}
```

`checkAccess` is a no-op and `additionalFilter` returns `null` by default, **so a `/history` endpoint that does not
override it exposes the history of every row, across all tenants.** Services with tenant-scoped entities must override
`additionalFilter`. `AuditPropertyFilter.property` must match a field name on the audited entity (so the column is in the
`_aud` table). The filter is combined with `from`/`to` and evaluated by Hibernate Envers
(`AuditEntity.property(...).in(...)`). If `allowedValues` is empty (the user has access to no tenants), an empty page
(`totalElements = 0`) is returned without querying the database.

**Deletions and `store_data_at_delete`:** by default Envers stores only `id` in DEL rows, so a tenant field such as
`fromApplicationId` is `null` in the delete revision and would drop out of a property-filtered `/history` lookup.
Deletions would then be missing from the tenant-scoped list. The starter therefore sets
`org.hibernate.envers.store_data_at_delete=true` (with a `HibernatePropertiesCustomizer`), so DEL rows keep the tenant
field and are included. `snapshot` is still `null` for deleted revisions. Override it if needed with
`spring.jpa.properties.org.hibernate.envers.store_data_at_delete=false`.

## Resolving `createdBy` and `lastModifiedBy` in REST DTOs

The entity stores `createdBy` as a structured [`Actor`](src/main/kotlin/no/novari/flyt/audit/actor/Actor.kt) (JSONB).
In the REST response the web client usually wants the person's name, not `{"type":"USER","oid":"..."}`. The recommended
contract is therefore a sidecar pattern:

```json
{
  "createdAt": "2026-06-17T10:00:00Z",
  "createdBy": "Ola Nordmann",
  "createdByActor": { "type": "USER", "oid": "8f2c..." },
  "lastModifiedAt": "2026-06-18T12:34:00Z",
  "lastModifiedBy": "System",
  "lastModifiedByActor": { "type": "SYSTEM" }
}
```

`createdBy: String?` is the resolved display name (the same contract as older services that exposed the name as a
string). `createdByActor: Actor?` is the structured value for clients that filter on `oid`.

### `ActorDisplayResolver`

The starter registers an [`ActorDisplayResolver`](src/main/kotlin/no/novari/flyt/audit/actor/ActorDisplayResolver.kt)
bean. Use it in the service's mapping layer:

```kotlin
@Service
class MyEntityMappingService(private val resolver: ActorDisplayResolver) {
    fun toDto(entity: MyEntity) = MyEntityDto(
        // ...
        createdAt = entity.createdAt,
        createdBy = resolver.resolve(entity.createdBy),
        createdByActor = entity.createdBy,
    )

    fun toDtos(entities: List<MyEntity>): List<MyEntityDto> {
        val displays = resolver.resolveAll(entities.map { it.createdBy })
        return entities.map { entity ->
            MyEntityDto(
                createdAt = entity.createdAt,
                createdBy = displays[entity.createdBy],
                createdByActor = entity.createdBy,
            )
        }
    }
}
```

`resolveAll` makes one batch call per page. Use it for lists.

### Fallback values

The defaults are Norwegian, because they are shown in the FLYT frontend.

| Actor type       | Default value                                     |
| ---------------- | ------------------------------------------------- |
| `User` (found)   | Name from `fint-flyt-authorization-service`       |
| `User` (missing) | `null` (can be overridden, e.g. "Ukjent bruker")  |
| `System`         | `"System"`                                        |
| `M2M`            | `clientId` (can be overridden with a fixed label) |
| `Unknown`        | `"Ukjent"`                                        |

Override the fallbacks per service in `application.yaml`:

```yaml
novari:
  flyt:
    audit:
      display:
        system: "FLYT-plattform"
        unknown-user: "Ukjent bruker"
        unknown: null    # returns null instead of "Ukjent"
        m2m: "Systemintegrasjon"
```

### Local name resolution (authorization-service itself)

The default implementation ([`HttpActorNameLookup`](src/main/kotlin/no/novari/flyt/audit/actor/ActorNameLookup.kt))
calls `fint-flyt-authorization-service` over HTTP. authorization-service must not call itself, so it registers its own
`ActorNameLookup` bean that looks up locally:

```kotlin
@Configuration
class LocalActorNameLookupConfig {
    @Bean
    fun localActorNameLookup(userRepository: UserRepository): ActorNameLookup =
        ActorNameLookup { oids ->
            userRepository.findAllByObjectIdentifierIn(oids)
                .associate { it.objectIdentifier to it.name }
        }
}
```

This bean replaces the default through `@ConditionalOnMissingBean(ActorNameLookup::class)`. With a local lookup, the
service does not need the [`AuthorizationClient`](src/main/kotlin/no/novari/flyt/audit/authorization/AuthorizationClient.kt)
or an OAuth2 client.

## OAuth2 setup (name resolution in the history API and REST DTOs)

The starter calls `fint-flyt-authorization-service` to look up the user's name at presentation time. This needs an
OAuth2 `client_credentials` setup in three steps.

> **Fail open:** name resolution is best effort. Without the OAuth2 setup the service **does not crash**: the starter
> falls back to [`NoOpActorNameLookup`](src/main/kotlin/no/novari/flyt/audit/actor/ActorNameLookup.kt) and logs a
> `WARN` at startup, and `createdBy`/`lastModifiedBy`/`actorDisplay` become `null` (or the configured fallback) instead of
> a name. If your service needs names, check that the `WARN` is not logged at startup. Runtime errors from
> authorization-service (downtime, 401) are handled the same way: the response is returned without names instead of
> failing.

### Step 1: Gradle dependency

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
}
```

### Step 2: Kustomize CRD

Add `kustomize/base/oauth2-authorization-client.yaml`:

```yaml
apiVersion: "fintlabs.no/v1alpha1"
kind: NamOAuthClientApplicationResource
metadata:
  name: fint-flyt-authorization-oauth2-client
spec:
  grantTypes:
    - client_credentials
```

Reference the CRD in `flais.yaml` and map the keys to service-specific environment variables (the same convention as
authorization-service itself):

```yaml
env:
  - name: fint.flyt.authorization.sso.client-id
    valueFrom:
      secretKeyRef:
        name: fint-flyt-authorization-oauth2-client
        key: fint.sso.client-id
  - name: fint.flyt.authorization.sso.client-secret
    valueFrom:
      secretKeyRef:
        name: fint-flyt-authorization-oauth2-client
        key: fint.sso.client-secret
```

### Step 3: Spring Security profile

Add `src/main/resources/application-flyt-authorization-client.yaml`:

```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          fint-idp:
            token-uri: https://idp.felleskomponent.no/nidp/oauth/nam/token
        registration:
          authorization-service:
            authorization-grant-type: client_credentials
            client-id: ${fint.flyt.authorization.sso.client-id}
            client-secret: ${fint.flyt.authorization.sso.client-secret}
            provider: fint-idp
```

No starter registers `provider.fint-idp`, so it must be declared as above (all FLYT services use the same `token-uri`).

`novari.flyt.audit.authorization.base-url` is `http://fint-flyt-authorization-service:8080` by default.

**Note:** FLYT services are usually deployed with `server.servlet.context-path` set per environment and tenant (e.g.
`/beta/afk-no`), injected as an environment variable by the flais CRD. `fint-flyt-authorization-service` is deployed the
same way, so an internal call to `http://fint-flyt-authorization-service:8080` without the context path gives 404 there.
The app and authorization-service run in the same environment and tenant and share the context path, so use
`${server.servlet.context-path:}` to reuse the service's own value:

```yaml
novari:
  flyt:
    audit:
      authorization:
        base-url: 'http://fint-flyt-authorization-service:8080${server.servlet.context-path:}'
```

Override it with a fixed value only when needed, e.g. in `local-staging` (where `server.servlet.context-path` is not
set):

```yaml
novari:
  flyt:
    audit:
      authorization:
        base-url: 'http://localhost:8084'
```

The registration ID in `spring.security.oauth2.client.registration` must match
`novari.flyt.audit.authorization.client-registration-id` (`authorization-service` by default). If you use another name,
set the property:

```yaml
novari:
  flyt:
    audit:
      authorization:
        client-registration-id: my-own-name
```

Activate the profile in `application.yaml`:

```yaml
spring:
  profiles:
    include: flyt-authorization-client
```

The environment variables `fint.flyt.authorization.sso.client-id` and `fint.flyt.authorization.sso.client-secret` are set
by the Kustomize CRD in step 2. In `local-staging` they must be set by hand or in `.env`.

> **Naming:** the prefix `fint.flyt.authorization.sso` shows which service the credentials belong to. authorization-service
> uses the same convention, which makes debugging across services easier.

### Without name resolution

Services that only use variant B or C (no history API) never call `AuthorizationClient`, so the OAuth2 setup can be left
out.

## Configuration properties

| Property                                                 | Default                                       | Description                                            |
| -------------------------------------------------------- | --------------------------------------------- | ------------------------------------------------------ |
| `novari.flyt.audit.authorization.base-url`               | `http://fint-flyt-authorization-service:8080` | Base URL of authorization-service                      |
| `novari.flyt.audit.authorization.client-registration-id` | `authorization-service`                       | Spring Security OAuth2 registration ID                 |
| `novari.flyt.audit.authorization.cache.enabled`          | `true`                                        | Caffeine cache for name lookups                        |
| `novari.flyt.audit.authorization.cache.ttl`              | `5m`                                          | Cache time to live                                     |
| `novari.flyt.audit.authorization.cache.max-size`         | `10000`                                       | Maximum number of cache entries                        |
| `novari.flyt.audit.display.system`                       | `System`                                      | Display value for `Actor.System`                       |
| `novari.flyt.audit.display.unknown`                      | `Ukjent`                                      | Display value for `Actor.Unknown`                      |
| `novari.flyt.audit.display.unknown-user`                 | `null`                                        | Display value for a `User` that was not found          |
| `novari.flyt.audit.display.m2m`                          | `null`                                        | Display value for `Actor.M2M` (`null` uses `clientId`) |

## Upgrading from 1.x to 2.0

- Spring Boot 4, Hibernate 7 and Jackson 3 are required. The JSON format of `Actor`, in the database and in the
  `flyt.actor` header, is unchanged.
- `@EnableFlytAuditing` is removed. Delete it from the application class, since the auto-configurations already do
  everything.
- `ApplicationContextHolder` is removed. Hibernate creates `ActorRevisionListener` and `AuditMetricsListener` through
  Spring's bean container. Tests that registered `ApplicationContextHolder` themselves can drop it.
- The OAuth2 starter is `spring-boot-starter-security-oauth2-client` in Spring Boot 4.

## Building locally

```bash
./gradlew check
```

Requires JDK 25 (Temurin) and Docker (Testcontainers).

## Publishing

Published to Reposilite by GitHub Actions on a GitHub release. The tag format is `vX.Y.Z`, and the version is `X.Y.Z`
(without `v`).

## Dependency locking

The build uses [Gradle dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html), so every
build resolves exactly the same direct and transitive versions. The locked versions are in the committed
`gradle.lockfile` files, so a pull request shows every transitive version change it causes. Gradle plugins are not
locked, because Dependabot only updates `gradle.lockfile`. Their versions are in `gradle/libs.versions.toml`.

**Automatic (Dependabot):** Dependabot updates the lockfiles in its own pull requests, so no manual step is needed. If two
Dependabot pull requests conflict in a lockfile, Dependabot rebases the remaining one after the first is merged. If it
does not, comment `@dependabot recreate` on the pull request.

**Manual:** any change by hand that affects dependency resolution requires regenerating the lockfiles. That covers
versions, constraints, and adding or removing a dependency:

```bash
./gradlew dependencies --write-locks
```

Commit the updated lockfiles with the change. If you forget, the build fails locally and in CI with an error saying the
resolved version "is not part of the dependency lock state".
