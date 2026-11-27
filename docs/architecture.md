# Architecture

C4 level 3 for flyt-audit-starter. The starter has no system context or containers of its own: it runs inside each
Spring Boot app that depends on it. The diagram shows that app with the starter's components. Blue is the starter,
turquoise is the app, Hibernate and authorization-service.

## C4 level 3: Components

```mermaid
C4Component
    title flyt-audit-starter in a Spring Boot app

    Component_Ext(authz, "authorization-service", "HTTP", "User names by OID")
    Component(lookup, "Name lookup", "RestClient, Caffeine", "OAuth2 client with cache")
    Component_Ext(app, "App code", "Kotlin", "Entities, mapping, controllers")
    Component(resolver, "ActorDisplayResolver", "Kotlin", "Actor to display name")
    Component_Ext(jpa, "JPA and Envers", "Hibernate, Postgres", "Entities, _aud tables, revinfo")
    Component(history, "History API", "EnversHistoryService", "Revisions newest first")
    Component(auditor, "ActorAuditorAware", "Spring Data", "ActorContext, then JWT")
    Component(revision, "ActorRevisionListener", "Envers", "Actor on each revision")

    Rel(lookup, authz, "Looks up", "HTTP")
    Rel(resolver, lookup, "OIDs")
    Rel(app, resolver, "Names")
    Rel(app, jpa, "Saves")
    Rel(app, history, "Extends")
    Rel(history, resolver, "Names")
    Rel(history, jpa, "Revisions")
    Rel(jpa, auditor, "createdBy")
    Rel(jpa, revision, "New revision")
    Rel(revision, auditor, "Actor")

    UpdateRelStyle(lookup, authz, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(resolver, lookup, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(app, resolver, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(app, jpa, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(app, history, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(history, resolver, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(history, jpa, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(jpa, auditor, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(jpa, revision, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(revision, auditor, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateElementStyle(lookup, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(resolver, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(history, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(auditor, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(revision, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(authz, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(app, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(jpa, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

- **Recording the actor.** Entities extend `CreatedAuditedEntity` or `AuditedEntity`. Spring Data JPA auditing asks
  `ActorAuditorAware` for `createdBy` and `lastModifiedBy` when an entity is persisted or updated. It uses the actor bound
  by `ActorContext.withActor` (for Kafka listeners, from the `flyt.actor` header), then the JWT in the security context,
  and otherwise `Actor.System`. For `@Audited` entities, Envers creates a revision when the transaction commits, and
  `ActorRevisionListener` stores the same actor in `revinfo`. Hibernate creates the listener through Spring's bean
  container, so it gets `flytAuditorAware` injected. `AuditMetricsListener` counts writes in the same way.
- **Reading history.** `EnversHistoryService` reads revisions with the Envers `AuditReader`, newest first, and
  `HistoryControllerSupport` exposes them as `GET /{id}/history` and `GET /history`. The app extends both, chooses the
  snapshot shape, and secures the endpoints.
- **Display names.** `Actor` stores only the OID of a user. `ActorDisplayResolver` resolves names at presentation time,
  in one batch per page, through `ActorNameLookup`. The default lookup calls authorization-service with an OAuth2
  `client_credentials` `RestClient` and a Caffeine cache. authorization-service registers its own local lookup instead.
  Without an OAuth2 client, the starter falls back to a lookup that resolves no names.
- **Auto-configuration.** `FlytAuditAutoConfiguration`, `FlytAuditJpaAuditingAutoConfiguration` and
  `AuthorizationRestClientConfiguration` set all of this up. Every bean the app may replace is `@ConditionalOnMissingBean`.

Not in the starter: Flyway migrations for the app's tables (only a template for `revinfo`), authorization of the history
endpoints, and storing personal data such as names.
