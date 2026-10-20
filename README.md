# eCMR Backend (CargoTrack)

CargoTrack build of the [Open Logistics Foundation eCMR backend](https://git.openlogisticsfoundation.org/wg-electronictransportdocuments/ecmr/ecmr-backend).

This repository is an **overlay**, not a fork: it does not contain the upstream source code.
The build downloads upstream at a pinned commit and adds CargoTrack's files on top.
Upstream stays the single source of truth, and updating it means changing one line.

```
upstream.lock     pinned upstream commits: ecmr-backend (bumped by PR, see below) and eSeal
overlay/          new files copied into the upstream tree (must not override upstream files)
patches/          changes to upstream files - should stay empty, see patches/README.md
docker/           runtime image
config/           environment variable reference
scripts/          assemble.sh - builds the source tree in build/src and the eSeal source in build/eseal
```

eSeal signer/verifier are built from source at `ESEAL_SHA` instead of being taken from the OLF Maven registry,
where released versions have been overwritten (1.2.0, 2026-10-19) and deleted (1.2.1).
`ESEAL_SHA` must produce the version the backend `pom.xml` expects (`version=` in eSeal's `gradle.properties`).

## What the overlay adds

| File | Purpose |
|---|---|
| `overlay/.../cargotrack/sms/SmsoSmsProvider.java` | Sends guest TAN SMS through SMSO (`cargotrack.sms.enabled=true`, API key + sender from env, optional Romanian template); otherwise upstream's logging dummy is used |
| `overlay/.../application-cargotrack.properties` | `cargotrack` Spring profile: own Liquibase entry point, storage path, SMS settings |
| `overlay/.../db/cargotrack/changelog-master.xml` | Runs the full upstream changelog, then CargoTrack changesets |
| `.../001-bootstrap-admin.xml` | First admin from `SPRING_LIQUIBASE_PARAMETERS_BOOTSTRAPADMINEMAIL` (startup halts if unset on a fresh DB) |
| `.../002-deactivate-upstream-seed-users.xml` | Deactivates the 10 admin accounts that upstream seeds (users are matched by e-mail claim) |
| `overlay/cargotrack/olf-artifacts.sha256` + `verify-olf-artifacts.sh` | SHA-256 pins for the jars still taken from the OLF registry (data model). The OLF Maven registry allows re-publishing a released version, and eSeal 1.2.0 was overwritten with incompatible content on 2026-10-19. Docker and CI builds fail on any drift |

## Current patches

None.

Custom Spring beans must live under `org.openlogisticsfoundation.ecmr` (upstream component scan), e.g. `org.openlogisticsfoundation.ecmr.cargotrack.*`.

## Build locally

Requires Git Bash and Docker (no local Java/Maven needed):

```bash
scripts/assemble.sh
docker build -f docker/Dockerfile -t ecmr-backend:local build
```

With JDK 21 and Maven 3.9 installed, you can also run the upstream test suite (needs Docker for Testcontainers):
first `cd build/eseal && ./gradlew :signer:publishToMavenLocal :verifier:publishToMavenLocal -x test`, then `mvn -f build/src/pom.xml verify`.

## Local stack (Keycloak, PostgreSQL, Mailpit, frontend)

[`dev/docker-compose.yml`](dev/docker-compose.yml) runs the locally built `ecmr-backend:local` and `ecmr-frontend:local`
images (see the comment at the top of the file) with a dev Keycloak realm ([`dev/keycloak/cargotrack-realm.json`](dev/keycloak/cargotrack-realm.json)):

```bash
docker compose -f dev/docker-compose.yml up -d      # down: stop, down -v: also delete the data
```

| | |
|---|---|
| Frontend | http://localhost:8082 - `admin@cargotrack.local` / `admin` (bootstrap admin) |
| Keycloak | http://localhost:8080 - `admin` / `admin`; realm user `dispecer@cargotrack.local` / `dispecer` needs an eCMR user with that e-mail |
| Backend | http://localhost:8081/api/swagger-ui |
| Mailpit | http://localhost:8025 - all mails sent by the backend |

Signs with the upstream test keystore - development only. Real TAN SMS: put the `CARGOTRACK_SMS_*` variables into `dev/.env` (git-ignored).

## Run

Production deployment (backend, frontend, Keycloak, eSeal key, DNS, OLF validation): [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).

See [`config/backend.env.example`](config/backend.env.example) for the required environment variables.
Activate the `jobs` profile on exactly one instance. The upstream `docker-compose.yml` is for demos only, not for production.

## CI

- **Build** (`.github/workflows/build.yml`): assemble → upstream test suite → image.
  Pushes to `ghcr.io/cargotracksolutions/ecmr-backend:<overlay-sha>-up<upstream-sha>` and `:main` from `main`.
- **Upstream bump** (`.github/workflows/upstream-bump.yml`): runs weekly or manually and opens a PR that moves `upstream.lock` to the latest upstream `main`.
  The PR body includes the commit list, the `API_CHANGELOG.md` diff, and changed migrations/OpenAPI files.
  Set secret `UPSTREAM_BUMP_TOKEN` so the bump PRs get CI checks (PRs opened with `GITHUB_TOKEN` don't trigger workflows).

### Upgrade procedure

1. Review the bump PR: breaking API changes, new migrations, data model/eSeal versions in `pom.xml`.
   If the eSeal version changed, move `ESEAL_SHA` in `upstream.lock` to an eSeal commit with that version.
   If the OLF checksum step fails, verify the new artifacts are intended, then copy the printed "actual" lines into `overlay/cargotrack/olf-artifacts.sha256`.
   Drop patches that upstream now includes.
2. Regenerate the frontend API client against the new revision (`documentation/openapi/frontend/openapi.yml`) and fix compile errors.
3. Merge → deploy to staging → smoke test (create, seal, share, guest TAN) → production.

## Changes to upstream behaviour

Prefer contributing upstream (merge request to the OLF repository). If a fix is needed before it is merged, add it to `patches/` with a link to the MR and remove it once upstream includes it.

## License

Upstream: Open Logistics Foundation License 1.3. CargoTrack overlay code: proprietary, © CargoTrack.
