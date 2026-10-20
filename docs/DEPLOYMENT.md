# eCMR production deployment (CargoTrack)

Deployment checklist for the CargoTrack eCMR platform: backend (this repository), frontend
([ecmr-frontend](https://github.com/CargoTrackSolutions/ecmr-frontend)), Keycloak, PostgreSQL.
The same setup is what the Open Logistics Foundation validates before CargoTrack is listed as an eCMR provider
(see [OLF validation](#olf-validation)).

Host names below are suggestions; whatever you choose, use it consistently in all settings.

| Component | Public URL | Image / source |
|---|---|---|
| Frontend | `https://ecmr.cargotrack.ro` | `ghcr.io/cargotracksolutions/ecmr-frontend:main` |
| Backend API | `https://ecmr-api.cargotrack.ro/api` | `ghcr.io/cargotracksolutions/ecmr-backend:main` |
| Identity provider | `https://login.cargotrack.ro/realms/cargotrack` | Keycloak 26 (or an existing CargoTrack instance) |
| Database | internal | PostgreSQL 16/17 |

Images are tagged `<overlay-sha>-up<upstream-sha>` per build; pin those tags in production, `:main` is the latest build.
The GHCR packages are private: pulling needs a token with `read:packages`.

## 1. Backend

- Environment: [`config/backend.env.example`](../config/backend.env.example) lists every variable.
  - `APP_ORIGIN_URL=https://ecmr-api.cargotrack.ro` - written into every seal as origin; other instances call it
    and look up our DNS fingerprint on its host name. Must be the public HTTPS URL; changing it later breaks the
    verification of existing seals.
  - `APP_FRONTEND_URL` / `WEB_CORS_ORIGINS` = the frontend URL.
  - `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI` = the Keycloak realm URL; it must be reachable from the
    backend at startup and identical to the issuer the browser sees.
  - `SPRING_LIQUIBASE_PARAMETERS_BOOTSTRAPADMINEMAIL` = e-mail of the first eCMR administrator (Keycloak account with
    the same verified e-mail). Startup halts on an empty database if it is missing.
  - SMTP (`SPRING_MAIL_*`, `MAIL_FROM`) and SMSO (`CARGOTRACK_SMS_*`).
- `SPRING_PROFILES_ACTIVE=cargotrack,jobs` on **exactly one** instance (the `jobs` profile processes incoming eCMR
  imports and synchronization), `cargotrack` on any further instances.
- Port 8080, context path `/api`. Health: `GET /api/actuator/health`. `/api/actuator/prometheus` is also exposed -
  do not publish `/api/actuator/*` beyond health on the internet.
- Volumes: `/data/attachments` (persistent, attachments) and `/secrets/eseal.p12` (read-only, see 4).
- Reverse proxy: TLS termination, request body limit of at least the attachment size you allow
  (the application accepts up to 1000 MB).

## 2. Frontend

- Environment: [`config/frontend.env.example`](https://github.com/CargoTrackSolutions/ecmr-frontend/blob/main/config/frontend.env.example)
  (`BACKEND_API_URL`, `OAUTH_ISSUER`, `OAUTH_CLIENT_ID`; `FRAME_ANCESTORS` defaults to `'self' https://*.cargotrack.ro`
  for embedding in the CargoTrack Web App).
- Port 8080, health `GET /healthz`. Stateless.

## 3. Keycloak

Realm `cargotrack` (template: [`dev/keycloak/cargotrack-realm.json`](../dev/keycloak/cargotrack-realm.json) without
its demo users, with `sslRequired: external`):

- Client `ecmr-frontend`: public, standard flow, PKCE `S256`, no direct access grants.
  Redirect URIs `https://ecmr.cargotrack.ro/*` (+ `https://ecmr-api.cargotrack.ro/api/swagger-ui/oauth2-redirect.html`
  if Swagger login is wanted), web origins `https://ecmr.cargotrack.ro`, post-logout redirect `https://ecmr.cargotrack.ro/*`.
- Every user needs an **e-mail address** in Keycloak (verified, or created by an admin): the backend identifies
  users by the `email` claim.
  An eCMR user (created in the eCMR user management) and a Keycloak account must exist with the same e-mail.
- Iframe embedding: Realm settings → Security defenses → `Content-Security-Policy:
  frame-src 'self'; frame-ancestors 'self' https://*.cargotrack.ro; object-src 'none';`, `X-Frame-Options` empty.
- SMTP for password-reset mails.

## 4. eSeal signing identity

Every seal is signed with the instance key; other instances trust it through a DNS record.

```bash
scripts/eseal-keystore.sh ./eseal ecmr-api.cargotrack.ro 1095    # asks for the keystore password
```

Creates `eseal/eseal.p12` (alias `cargotrack-ecmr`, self-signed RSA 3072) and `eseal/eseal-cert.pem`, and prints the
DNS record and the `ECMR_SEAL_SIGN_*` settings. Store the PKCS12 and its password in the secret store and back them up.
For a purchased (e.g. eIDAS-qualified) seal certificate, put it into a PKCS12 with that alias and run
`scripts/eseal-fingerprint.sh <cert.pem>` for the DNS value.

Never use the upstream test keystore in production: its private key is public.

## 5. DNS

- `ecmr.cargotrack.ro`, `ecmr-api.cargotrack.ro`, the Keycloak host → reverse proxy.
- TXT record on the host of `APP_ORIGIN_URL`:

  ```
  ecmr-api.cargotrack.ro.  TXT  "ecmr-sha256-fingerprint=<value printed by the script>"
  ```

  Verifying instances look it up on that host first, then on the parent domains (`cargotrack.ro`).
  Several TXT records are allowed: when the key is rotated, **keep the old fingerprint** as long as documents
  sealed with the old key are in circulation, or their seals stop verifying.
- Check: `dig +short TXT ecmr-api.cargotrack.ro`

## 6. After the first start

1. Sign in with the bootstrap admin, create the CargoTrack groups and users.
2. Instance management (admin menu): approve the URLs of partner instances (e.g. the OLF validation platform)
   and map their e-mail domains, so shares to their users are routed to their instance.
3. Smoke test: create an eCMR, seal it, share it to a second user, guest TAN by SMS, PDF download.

## Backups

PostgreSQL, `/data/attachments`, `eseal.p12` + password, the Keycloak database.

## OLF validation

Prerequisites: public HTTPS backend, own eSeal identity with the DNS TXT record, `jobs` profile, SMTP, the OLF
validation platform approved in instance management, and a receiving user (consignee) with a default group.
Process: we seal an eCMR and send it to the validation platform; it seals as carrier and returns it; we verify and
seal as consignee. Contact: technical@openlogisticsfoundation.org.

Known risk: this build uses eSeal 1.2.0 (DSS 6.1) built from source (see `upstream.lock`); confirm with OLF which
eSeal version the validation platform runs before the exchange.
