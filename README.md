# fint-flyt-audit-starter

Bibliotek for å håndtere endringslogg, og generell audit-logging i FLYT.

## Status

Under oppbygging. Prosjekt-skjelettet er på plass (FFS-2102). Konkrete komponenter implementeres i påfølgende oppgaver — se Epic FFS-2069 for full liste.

## Bruk (kommer)

Når starteren er ferdigstilt og publisert:

```kotlin
dependencies {
    implementation("no.novari:flyt-audit-starter:<versjon>")
}
```

```kotlin
@SpringBootApplication
@EnableFlytAuditing
class MyServiceApplication
```

Detaljer om aktør-modell, audit-entitet-baseklasser, historikk-API og auth-service-integrasjon kommer etter hvert som komponentene er på plass.

## Bygging lokalt

```bash
./gradlew build
```

Krever JDK 25 (Temurin).

## Publisering

Publiseres til Reposilite via GitHub Actions ved GitHub Release. Tag-formatet er `vX.Y.Z`; versjonen blir `X.Y.Z` (uten `v`).
