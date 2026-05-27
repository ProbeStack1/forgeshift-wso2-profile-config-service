# forgeshift-wso2-profile-config-service

Centralized vault for connection profiles used by the Forgeshift WSO2 to Kong
Konnect migrator stack. Mirrors the Apigee-side
`probestack-profile-config-service` one-for-one, with WSO2 replacing Apigee Edge/X.

Manages three provider types:

| Provider | Stores | Verify endpoint calls |
|---|---|---|
| **WSO2 API Manager** | host, username/password, OAuth2 client_id/secret | `/oauth2/token` then optionally `/api/am/admin/v4/tenant-info/admin` |
| **Kong Konnect** | base URL, region, Personal Access Token, control-plane id | `GET /v2/control-planes/{cpId}` |
| **Google Cloud Storage** | service-account JSON (base64), bucket, prefix | `storage.get(bucket)` + 1-object list probe |

Multi-tenant via the `X-Partner-Id` header. Audit log per mutation. Secrets
masked on every read. Same MongoDB cluster as the discovery service — they
share the `profiles` collection.

---

## REST API at a glance

Context path: `/wso2/config/v1`.

| Group | Surface |
|---|---|
| WSO2 profiles | `POST/PUT/GET/DELETE /wso2/profiles` + `POST /wso2/profiles/verify` + `POST /wso2/profiles/verify-saved` |
| Kong Konnect profiles | `POST/PUT/GET/DELETE /kong-konnect/profiles` + `POST /kong-konnect/profiles/verify` + `POST /kong-konnect/profiles/verify-saved` |
| Cloud Storage profiles | `POST/PUT/GET/DELETE /cloud-storage/profiles` + multipart `POST /cloud-storage/profiles/upload` + `POST /cloud-storage/profiles/verify-upload` + `POST /cloud-storage/profiles/verify-saved` |
| Health & metadata | `/actuator/health`, `/swagger-ui.html`, `/v3/api-docs` |

Swagger UI: <http://localhost:8082/wso2/config/v1/swagger-ui.html>.
Full reference: `postman/forgeshift-wso2-profile-config.postman_collection.json`.

## MongoDB collections

| Collection | Holds | Written by |
|---|---|---|
| `profiles` | WSO2 connection profiles (shared with the discovery service) | `Wso2ProfileService` |
| `kong_konnect_profiles` | Kong Konnect PATs + control plane ids | `KongKonnectProfileService` |
| `cloud_storage_profiles` | GCS service-account JSON (base64) + bucket binding | `CloudStorageProfileService` |
| `profile_audit_log` | Async audit of every mutation | `ProfileAuditService` |
| `tenant_configurations` | Per-(X-Partner-Id) tenant config | (admin reads) |

## Verify endpoints

Every provider has a `/verify` endpoint that calls the upstream system with
the supplied credentials and reports success / latency / a 6-char token prefix
(never the full token). Use this to test credentials before saving a profile,
or `verify-saved` to re-validate an existing profile (which also stamps
`lastVerifiedAt` on the document).

## Multi-tenancy

`X-Partner-Id` header is required on every request. Defaults to
`forgeshift.profile-config.tenant.default-tenant` (set to `probestack`) when
absent. Stored on a thread-local `TenantContext` for the duration of the
request. Future per-tenant filtering on the audit log uses this.

## Configuration

| Env var | Property | Default |
|---|---|---|
| `MONGODB_URI` | `spring.data.mongodb.uri` | `mongodb://localhost:27017/forgeshift_profiles` |
| `MONGODB_DATABASE` | `spring.data.mongodb.database` | `forgeshift_profiles` |
| `SERVER_PORT` | `server.port` | `8082` |
| `TENANT_HEADER` | `forgeshift.profile-config.tenant.header-name` | `X-Partner-Id` |
| `DEFAULT_TENANT` | `forgeshift.profile-config.tenant.default-tenant` | `probestack` |
| `AUDIT_ENABLED` | `forgeshift.profile-config.audit.enabled` | `true` |
| `CORS_ORIGINS` | `cors.origins` | `http://localhost:5173` |

## Build & run

```bash
# 1. Copy .env.example to .env and edit
# 2. Build + run
mvn clean package
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Or full container
docker build -t forgeshift-wso2-profile-config-service .
docker run -p 8082:8082 --env-file .env forgeshift-wso2-profile-config-service
```

## Try the full flow

```bash
# 1. Save a WSO2 profile (uses the static .env on the discovery side)
curl -X POST http://localhost:8082/wso2/config/v1/wso2/profiles \
  -H "Content-Type: application/json" \
  -H "X-Partner-Id: probestack" \
  -d '{"companyName":"probestack","wso2Tenant":"carbon.super","profileName":"primary",
       "wso2BaseUrl":"https://34.133.77.23:9443","username":"admin","password":"admin",
       "clientId":"<id>","clientSecret":"<secret>","trustSelfSigned":true,
       "userEmail":"sdmoh@local"}'

# 2. Verify it
curl -X POST "http://localhost:8082/wso2/config/v1/wso2/profiles/verify-saved?companyName=probestack&wso2Tenant=carbon.super&profileName=primary" \
  -H "X-Partner-Id: probestack"

# 3. Save a Kong Konnect profile
curl -X POST http://localhost:8082/wso2/config/v1/kong-konnect/profiles \
  -H "Content-Type: application/json" \
  -H "X-Partner-Id: probestack" \
  -d '{"companyName":"probestack","profileName":"primary",
       "konnectBaseUrl":"https://us.api.konghq.com","konnectAccessToken":"kpat_...",
       "controlPlaneId":"43dbc26e-...","region":"us","userEmail":"sdmoh@local"}'

# 4. Upload a GCS service-account
curl -X POST http://localhost:8082/wso2/config/v1/cloud-storage/profiles/upload \
  -H "X-Partner-Id: probestack" \
  -F "companyName=probestack" \
  -F "profileName=primary" \
  -F "bucket=forgeshift-staging" \
  -F "userEmail=sdmoh@local" \
  -F "serviceAccount=@/path/to/sa.json"

# 5. Verify the bucket is reachable
curl -X POST "http://localhost:8082/wso2/config/v1/cloud-storage/profiles/verify-saved?companyName=probestack&profileName=primary" \
  -H "X-Partner-Id: probestack"
```

## How the other services consume profiles

- The **discovery service** reads from the same `profiles` collection. Once
  you save a WSO2 profile here, any discovery call with the matching
  `(companyName, wso2Tenant)` automatically uses those credentials and
  bypasses the static `.env` fallback.
- The **migrator** (future service) will read `kong_konnect_profiles` to get
  the target control plane PAT + id, and `cloud_storage_profiles` to know
  where to write/read intermediate artifacts.

## Security notes

- Secrets (passwords, PATs, SA JSON) are stored in plaintext in MongoDB for
  the MVP. Production deployment must move these to a KMS-backed secret
  store and replace the inline fields with references.
- Responses always mask secrets — full values never leave the service.
- Audit log captures every CRUD. Add a TTL index on `requestedAt` in ops if
  you need automatic expiry.

## What's next

- Reveal endpoint for the GCS SA JSON (for migrator pickup) — currently the
  raw bytes never leave the service.
- Per-tenant Mongo databases (the reference uses one Mongo per partner via
  multi-tenancy library) — defer until needed.
- Encryption-at-rest for stored secrets via Spring Cloud Vault or KMS.
