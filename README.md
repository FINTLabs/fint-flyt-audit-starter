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

Publiseres til `https://repo.fintlabs.no/releases`. Krever JDK 25.

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

**Historikk-endepunktet** (`GET /{id}/history`) støtter `page`, `size` og `from`/`to`-filtrering. Sortering er fast: nyeste revisjon returneres alltid først og kan ikke overstyres via request-parametere.

> **Tilgangskontroll:** starteren legger ingen autentisering eller autorisasjon på historikk-endepunktet. Konsumenten er ansvarlig for å sikre det — typisk ved å montere kontrolleren under et internt path-prefix som er autentisert i NAM (f.eks. `no.novari.flyt.webresourceserver.UrlPaths.INTERNAL_API`), eller via Spring Security-konfigurasjon.

## OAuth2-oppsett (påkrevd for Variant D/E med historikk-API)

Starteren kaller `fint-flyt-authorization-service` for å hente brukerens navn ved presentasjons-tid. Dette krever OAuth2 `client_credentials`-oppsett med tre konkrete steg.

> **NB:** Mangler ett av stegene under, vil tjenesten **krasje ved oppstart** med en forklarende feilmelding.

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
        registration:
          authorization-service:
            authorization-grant-type: client_credentials
            client-id: ${fint.flyt.authorization.sso.client-id}
            client-secret: ${fint.flyt.authorization.sso.client-secret}
            provider: fint-idp
```

`novari.flyt.audit.authorization.base-url` trenger normalt ikke settes — den er som standard `http://fint-flyt-authorization-service:8080`, som er riktig i Kubernetes-miljøene. Overstyr kun ved behov, f.eks. i `local-staging`:

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

## Bygging lokalt

```bash
./gradlew check
```

Krever JDK 25 (Temurin) og Docker (Testcontainers).

## Publisering

Publiseres til Reposilite via GitHub Actions ved GitHub Release. Tag-formatet er `vX.Y.Z`; versjonen blir `X.Y.Z` (uten `v`).
