# fint-flyt-audit-starter

Spring Boot-starter for endringslogg i FLYT-tjenester.
Starteren leverer aktør-modell, `@MappedSuperclass`-hierarki, Hibernate Envers-integrasjon og historikk-API-støtte — slik at tjenester kan implementere endringslogg uten å bygge infrastrukturen på nytt.

## Innhold

- [`Actor`](src/main/kotlin/no/novari/flyt/audit/actor/Actor.kt) — forseglet interface med subtypene `User`, `System`, `M2M`, `Unknown`. Serialiseres som JSONB.
- [`ActorAuditorAware`](src/main/kotlin/no/novari/flyt/audit/actor/ActorAuditorAware.kt) — henter aktør fra JWT-sikkerhetskontekst (`objectidentifier` → `User`, `sub` → `M2M`, ingen JWT → `System`).
- [`CreatedAuditedEntity`](src/main/kotlin/no/novari/flyt/audit/entity/CreatedAuditedEntity.kt) — `@MappedSuperclass` med `createdAt` og `createdBy` (Variant B).
- [`AuditedEntity`](src/main/kotlin/no/novari/flyt/audit/entity/AuditedEntity.kt) — utvider `CreatedAuditedEntity` med `lastModifiedAt` og `lastModifiedBy` (Variant C/D/E).
- [`ActorRevisionEntity`](src/main/kotlin/no/novari/flyt/audit/revision/ActorRevisionEntity.kt) — Envers `@RevisionEntity` med JSONB `actor`-kolonne.
- [`AuthorizationClient`](src/main/kotlin/no/novari/flyt/audit/authorization/AuthorizationClient.kt) — klient mot `fint-flyt-authorization-service` for navn-oppslag ved presentasjons-tid.
- Flyway-migrasjonsmaler under [`src/main/resources/flyt-audit-templates/`](src/main/resources/flyt-audit-templates/) (bevisst utenfor `db/migration/` for å unngå auto-oppdagelse hos konsumenter — se under).

## Avhengighet

```kotlin
dependencies {
    implementation("no.novari:flyt-audit-starter:<versjon>")
}
```

Publiseres til `https://repo.fintlabs.no/releases`. Krever JDK 25 eller nyere.

## Kom i gang

### 1. Aktiver auditing

```kotlin
@SpringBootApplication
@EnableFlytAuditing
class MyServiceApplication
```

### 2. Velg variant

| Variant | Baseklasse                   | Gir deg                                         |
|---------|------------------------------|-------------------------------------------------|
| B       | `CreatedAuditedEntity`       | `createdAt` + `createdBy` (kun opprettelse)     |
| C       | `AuditedEntity`              | + `lastModifiedAt` + `lastModifiedBy`           |
| D       | `AuditedEntity` + `@Audited` | + full Envers-historikk i `_aud`-tabell         |
| E       | Variant D + historikk-API    | + REST-endepunkt via `HistoryControllerSupport` |

```kotlin
@Entity
@Audited          // kun for Variant D/E
@Table(name = "my_entity")
class MyEntity : AuditedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var name: String = ""
}
```

### 3. Flyway-migrasjoner

Starteren leverer [`V1__revinfo.sql`](src/main/resources/flyt-audit-templates/V1__revinfo.sql) (Envers `revinfo`-tabell og sekvens) **kun for Variant D/E** (Envers-historikk). Den ligger bevisst under `flyt-audit-templates/` og **ikke** under `db/migration/`, slik at Flyway hos konsumenten ikke auto-oppdager den (det ville kollidert med tjenestens egen `V1`). Kopier den til tjenestens `src/main/resources/db/migration/` og juster versjonsnummeret slik at det passer inn i tjenestens migrasjonsrekke.

Variant B/C trenger ikke `revinfo` i det hele tatt — kun audit-kolonnene på entitetstabellen (se under).

For entitetens audit-felt og `_aud`-tabellen skriver tjenesten selv migrasjonene.

**Eksempel — audit-felt på ny tabell (Variant C/D/E):**

```sql
ALTER TABLE my_entity
    ADD COLUMN created_at       TIMESTAMPTZ NULL,
    ADD COLUMN created_by       JSONB NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb,
    ADD COLUMN last_modified_at TIMESTAMPTZ NULL,
    ADD COLUMN last_modified_by JSONB NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb;
```

`*_at`-kolonnene er nullable — Spring Data setter dem automatisk ved første insert på nye rader.

**Retrofit av eksisterende tabell:** Hvis tabellen allerede har et `created_at`-felt med et annet navn eller type, bør verdiene kopieres over fremfor å settes til `now()`, da det er mer korrekt å la feltet stå `NULL` for rader som ble til før audit ble innført. For `TIMESTAMP → TIMESTAMPTZ`-konvertering er in-place-endring trygt:

```sql
ALTER TABLE my_entity
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
```

For `*_by`-kolonner som allerede inneholder strenger (navn, e-post, OID): er verdien en UUID kan den konverteres til `Actor.User`-format; ellers settes den til `SYSTEM` eller `UNKNOWN`:

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

**Eksempel — `_aud`-tabell (Variant D/E):**

```sql
CREATE TABLE my_entity_aud (
    id      BIGINT   NOT NULL,
    rev     BIGINT   NOT NULL REFERENCES revinfo(rev),
    revtype SMALLINT,
    name    VARCHAR(255),
    PRIMARY KEY (id, rev)
);
```

Legg til `spring.jpa.hibernate.ddl-auto: validate` i produksjonskonfigurasjon.

**Historikk-endepunktene** (`GET /{id}/history` og `GET /history`) støtter `page`, `size` og `from`/`to`-filtrering. Sortering er fast: nyeste revisjon returneres alltid først og kan ikke overstyres via request-parametere.

> **Tilgangskontroll:** starteren legger ingen autentisering eller autorisasjon på historikk-endepunktene i seg selv. Konsumenten er ansvarlig for å sikre dem — typisk ved å montere kontrolleren under et internt path-prefix som sikres av tjenestens egen resource-server (`no.novari:flyt-web-resource-server`), f.eks. `no.novari.flyt.webresourceserver.UrlPaths.INTERNAL_API` (som krever gyldig bruker-JWT + `USER`-rolle), eller via egen Spring Security-konfigurasjon.

### Autorisasjon per entitet/tenant

Er entiteten scopet til en bruker eller tenant (f.eks. en kildeapplikasjons-ID), holder ikke path-prefix-autentisering alene — `HistoryControllerSupport` gir to overstyrbare hooks for dette:

```kotlin
@RestController
@RequestMapping("/api/intern/min-tjeneste/ting")
class TingHistoryController(
    private val tingRepository: TingRepository,
    private val userAuthorizationService: UserAuthorizationService,
    historyService: TingHistoryService,
) : HistoryControllerSupport<Ting, Long, TingView>(historyService) {

    // Kalles før GET /{id}/history returnerer data. Kast for å nekte tilgang.
    override fun checkAccess(authentication: Authentication, id: Long) {
        val ting = tingRepository.findById(id).orElseThrow { TingNotFoundException(id) }
        userAuthorizationService.checkIfUserHasAccessToSourceApplication(authentication, ting.eierId)
    }

    // Kalles før GET /history (all-tenant-listen) returnerer data.
    override fun additionalFilter(authentication: Authentication): AuditPropertyFilter =
        AuditPropertyFilter(
            property = "eierId",
            allowedValues = userAuthorizationService.getUserAuthorizedSourceApplicationIds(authentication),
        )
}
```

`checkAccess` er no-op og `additionalFilter` returnerer `null` som standard — **et uoverstyrt `/history`-endepunkt eksponerer da endringshistorikk for samtlige rader, på tvers av eventuelle tenant-grenser.** Tjenester med tenant-scopede entiteter må aktivt overstyre `additionalFilter`. `AuditPropertyFilter.property` må matche et feltnavn på den auditerte entiteten (kolonnen finnes da i `_aud`-tabellen); filteret slås sammen med `from`/`to` og evaluares av Hibernate Envers (`AuditEntity.property(...).in(...)`). Er `allowedValues` tom (brukeren har tilgang til ingen tenants), returneres en tom side (`totalElements = 0`) uten å treffe databasen.

**Slettinger og `store_data_at_delete`:** Envers lagrer som standard kun `id` i DEL-rader, slik at et tenant-felt som `fromApplicationId` blir `null` i slette-revisjonen og ville falt ut av et property-filtrert `/history`-oppslag — slettinger ville da manglet i den tenant-scopede lista. Starteren aktiverer derfor `org.hibernate.envers.store_data_at_delete=true` automatisk (via en `HibernatePropertiesCustomizer`), slik at DEL-rader beholder tenant-feltet og korrekt inkluderes. `snapshot` er fortsatt `null` for slettede revisjoner. Overstyr ved behov med `spring.jpa.properties.org.hibernate.envers.store_data_at_delete=false`.

## Hydrering av `createdBy` / `lastModifiedBy` i REST-DTOer

Entiteten lagrer `createdBy` som strukturert [`Actor`](src/main/kotlin/no/novari/flyt/audit/actor/Actor.kt) (JSONB). I REST-responsen ønsker web-klienten typisk navnet på personen — ikke `{"type":"USER","oid":"..."}`. Anbefalt kontrakt mot web er derfor et sidecar-mønster:

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

`createdBy: String?` er hydrert visningsnavn (samme kontrakt som eldre tjenester som eksponerte navn som streng). `createdByActor: Actor?` er den strukturerte utvidelsen for klienter som trenger å filtrere på `oid`.

### `ActorDisplayResolver`

Starteren registrerer en [`ActorDisplayResolver`](src/main/kotlin/no/novari/flyt/audit/actor/ActorDisplayResolver.kt)-bønne automatisk (så snart en `ActorNameLookup`-implementasjon er tilgjengelig). Bruk den fra tjenestens mapping-lag:

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

`resolveAll` gjør ett batch-kall pr side og bør brukes for listeresponser.

### Fallback-verdier

| Actor-type      | Standard `String`-verdi                     |
|-----------------|---------------------------------------------|
| `User` (funnet) | Navn fra `fint-flyt-authorization-service`  |
| `User` (miss)   | `null` (kan overstyres til f.eks. "Ukjent bruker") |
| `System`        | `"System"`                                  |
| `M2M`           | `clientId` (kan overstyres til fast merkelapp) |
| `Unknown`       | `"Ukjent"`                                  |

Fallbacks kan overstyres per tjeneste i `application.yaml`:

```yaml
novari:
  flyt:
    audit:
      display:
        system: "FLYT-plattform"
        unknown-user: "Ukjent bruker"
        unknown: null    # returner null i stedet for "Ukjent"
        m2m: "Systemintegrasjon"
```

### Lokal hydrering (auth-service selv)

Default-implementasjonen ([`HttpActorNameLookup`](src/main/kotlin/no/novari/flyt/audit/actor/ActorNameLookup.kt)) kaller `fint-flyt-authorization-service` over HTTP. Auth-service selv skal ikke kalle seg selv — den registrerer en egen `ActorNameLookup`-bønne som slår opp lokalt:

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

Denne bønnen overstyrer default via `@ConditionalOnMissingBean(ActorNameLookup::class)`. Med lokal lookup trengs ikke [`AuthorizationClient`](src/main/kotlin/no/novari/flyt/audit/authorization/AuthorizationClient.kt)/OAuth2-klient-avhengigheten i den tjenesten.

## OAuth2-oppsett (for navnehydrering via historikk-API og REST-DTOer)

Starteren kaller `fint-flyt-authorization-service` for å hente brukerens navn ved presentasjons-tid. Dette krever OAuth2 `client_credentials`-oppsett med tre konkrete steg.

> **NB — fail-open:** Navnehydrering er "best effort". Mangler OAuth2-oppsettet, **krasjer ikke** tjenesten: starteren faller tilbake til [`NoOpActorNameLookup`](src/main/kotlin/no/novari/flyt/audit/actor/ActorNameLookup.kt) og logger en `WARN` ved oppstart, og `createdBy`/`lastModifiedBy`/`actorDisplay` blir da `null` (eller den konfigurerte fallback-verdien) i stedet for et navn. Er navn påkrevd i din tjeneste, verifiser `WARN`-loggen fravær ved oppstart. Kjøretidsfeil mot auth-service (nedetid, 401) håndteres likeledes failsafe — svaret returneres uten navn i stedet for å feile.

### Steg 1 — Gradle-avhengighet

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
}
```

### Steg 2 — Kustomize CRD

Legg til `kustomize/base/oauth2-authorization-client.yaml`:

```yaml
apiVersion: "fintlabs.no/v1alpha1"
kind: NamOAuthClientApplicationResource
metadata:
  name: fint-flyt-authorization-oauth2-client
spec:
  grantTypes:
    - client_credentials
```

Referer CRD-en i `flais.yaml` og map nøkkelen til tjeneste-spesifikk env-variabel (følger samme konvensjon som `authorization-service` selv bruker):

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

### Steg 3 — Spring Security-profil

Legg til `src/main/resources/application-flyt-authorization-client.yaml`:

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

`provider.fint-idp` registreres ikke automatisk av noen starter — den må deklareres eksplisitt som over (samme `token-uri` brukes av alle FLYT-tjenester).

`novari.flyt.audit.authorization.base-url` er som standard `http://fint-flyt-authorization-service:8080`.

**NB:** FLYT-tjenester deployes typisk med `server.servlet.context-path` satt per miljø/tenant
(f.eks. `/beta/afk-no`), injisert som miljøvariabel av "flais"-CRD-en. `fint-flyt-authorization-service`
er deployet med samme mønster, så et rent internt kall mot `http://fint-flyt-authorization-service:8080`
(uten context-path) gir 404 i disse miljøene. Siden konsumenten og `fint-flyt-authorization-service` er
ko-lokalisert i samme miljø/tenant, deler de samme context-path — bruk `${server.servlet.context-path:}`
til å gjenbruke tjenestens egen verdi:

```yaml
novari:
  flyt:
    audit:
      authorization:
        base-url: 'http://fint-flyt-authorization-service:8080${server.servlet.context-path:}'
```

Overstyr til en fast verdi kun ved behov, f.eks. i `local-staging` (som ikke har `server.servlet.context-path` satt):

```yaml
novari:
  flyt:
    audit:
      authorization:
        base-url: 'http://localhost:8084'
```

Registration-ID-en i `spring.security.oauth2.client.registration` må matche `novari.flyt.audit.authorization.client-registration-id` (default `authorization-service`). Bruker du et annet navn i registreringen, setter du tilsvarende property:

```yaml
novari:
  flyt:
    audit:
      authorization:
        client-registration-id: mitt-eget-navn
```

Aktiver profilen i `application.yaml`:

```yaml
spring:
  profiles:
    include: flyt-authorization-client
```

Env-variablene `fint.flyt.authorization.sso.client-id` og `fint.flyt.authorization.sso.client-secret` settes av Kustomize-CRD-en i steg 2. For `local-staging` må disse settes manuelt eller via `.env`.

> **Navnekonvensjon:** Prefikset `fint.flyt.authorization.sso` gjenspeiler hvilken tjeneste credentials-ene tilhører. `authorization-service` bruker samme konvensjon internt, noe som forenkler debugging på tvers av tjenester.

### Trenger du ikke navn-hydrering?

Tjenester som kun bruker Variant B/C (ingen historikk-API) kaller aldri `AuthorizationClient`. OAuth2-oppsettet kan da utelates.

## Konfigurasjonsproperties

| Property                                                 | Standard                                      | Beskrivelse                             |
|----------------------------------------------------------|-----------------------------------------------|-----------------------------------------|
| `novari.flyt.audit.authorization.base-url`               | `http://fint-flyt-authorization-service:8080` | Base-URL til auth-service               |
| `novari.flyt.audit.authorization.client-registration-id` | `authorization-service`                       | Spring Security OAuth2-registrerings-ID |
| `novari.flyt.audit.authorization.cache.enabled`          | `true`                                        | Aktiver Caffeine-cache for navn-oppslag |
| `novari.flyt.audit.authorization.cache.ttl`              | `5m`                                          | Cache time-to-live                      |
| `novari.flyt.audit.authorization.cache.max-size`         | `10000`                                       | Maks antall oppføringer i cache         |
| `novari.flyt.audit.display.system`                       | `System`                                      | Visningsverdi for `Actor.System`        |
| `novari.flyt.audit.display.unknown`                      | `Ukjent`                                      | Visningsverdi for `Actor.Unknown`       |
| `novari.flyt.audit.display.unknown-user`                 | `null`                                        | Visningsverdi for `User` som ikke ble funnet |
| `novari.flyt.audit.display.m2m`                          | `null`                                        | Visningsverdi for `Actor.M2M` (null → bruk `clientId`) |

## Bygging lokalt

```bash
./gradlew check
```

Krever JDK 25 (Temurin) og Docker (Testcontainers).

## Publisering

Publiseres til Reposilite via GitHub Actions ved GitHub Release. Tag-formatet er `vX.Y.Z`; versjonen blir `X.Y.Z` (uten `v`).
