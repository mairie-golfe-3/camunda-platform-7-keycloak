# ADR: Multi-tenancy via Referential Manager

**Status:** Implemented  
**Branch base:** v7.22.0 (ported to v7.24.0)

---

## Context

Upstream Camunda Platform 7 Keycloak plugin explicitly does not support tenant queries.
`KeycloakTenantQuery.executeList` returned an empty list, and calling `memberOfTenant()`
on a user or group query threw `UnsupportedOperationException`.

This fork requires Camunda multi-tenancy: processes and data are partitioned by tenant, and
Camunda needs to know which tenants a user belongs to and which groups/users belong to a tenant.

Keycloak holds user identity. Tenant ↔ user and tenant ↔ group mappings are owned by an
external service called the **Referential Manager**, which exposes a REST API.

---

## Decision

Implement tenant resolution by delegating to the Referential Manager, following the same
caching and query-routing patterns already used for user and group queries.

---

## Architecture

```
Camunda Engine
  │  TenantQuery
  │  UserQuery.memberOfTenant(tenantId)
  │  GroupQuery.memberOfTenant(tenantId)
  ▼
KeycloakIdentityProviderSession
  │
  ├─ tenantId set on query?
  │    ├─ UserQuery   → KeycloakUserService.requestUsersByTenantId()
  │    └─ GroupQuery  → KeycloakGroupService.requestGroupsByTenantId()
  │
  └─ TenantQuery
       ├─ userId set? → KeycloakTenantService.requestTenantsByUserId()
       └─ (none)      → KeycloakTenantService.requestTenantsWithoutUserId()
```

All tenant query results are cached via `tenantQueryCache` (same `cacheConfiguration` as
user/group caches — no separate tuning today).

---

## Referential Manager HTTP contract

The plugin calls these endpoints. No extra auth header is added — the underlying
`KeycloakRestTemplate` carries Keycloak's client-credentials token from the same interceptor
stack used for user/group calls.

| Method | Path | Called by |
|---|---|---|
| `GET` | `/tenants` | `KeycloakTenantService.requestTenantsWithoutUserId` |
| `GET` | `/users/{keycloakUserId}/tenants` | `KeycloakTenantService.requestTenantsByUserId` |
| `GET` | `/users?tenant={tenantId}` | `KeycloakUserService.requestUsersByTenantId` |
| `GET` | `/groups?tenant={tenantId}` | `KeycloakGroupService.requestGroupsByTenantId` |

**Response shapes:**

- Tenant object: `{ "tenant_id": "...", "name": "..." }` (snake_case)
- User objects: either Keycloak format (`firstName`/`lastName`) or Referential Manager format
  (`first_name`/`last_name`). The `transformUser` method falls back from camelCase to
  snake_case transparently.
- All arrays. A `404` on any endpoint is treated as empty result, not an error.

---

## Configuration

`referentialManagerUrl` is **mandatory**. The plugin validates it at startup in
`KeycloakIdentityProviderPlugin.validateConfiguration` and refuses to start if absent.

```yaml
# Spring Boot application.yaml
plugin.identity.keycloak:
  referentialManagerUrl: ${REFERENTIAL_MANAGER_URL:http://localhost:8080/api/v1/referential-manager}
```

```xml
<!-- Camunda cfg.xml (tests) -->
<property name="referentialManagerUrl" value="http://localhost:8080/api/v1/referential-manager" />
```

All 22 test `extension/src/test/resources/camunda.*.cfg.xml` files include this property.

---

## Code map

### New files

| File | Role |
|---|---|
| `extension/src/main/java/.../KeycloakTenantService.java` | REST client for all tenant endpoint calls; JSON → `TenantEntity` transformation; post-processing filter (id, ids, name, nameLike) with authorization check |
| `extension/src/main/java/.../CacheableKeycloakTenantQuery.java` | Immutable cache key for tenant queries (id, ids, name, nameLike, userId, groupId) with correct `equals`/`hashCode` |
| `extension/src/main/java/.../ReferentialTenant.java` | DTO (`tenant_id`, `name`). **Currently unused** — dead code, candidate for removal |

### Modified files

| File | Change |
|---|---|
| `KeycloakConfiguration.java` | New `referentialManagerUrl` property + getter/setter |
| `plugin/KeycloakIdentityProviderPlugin.java` | `referentialManagerUrl` added to mandatory-field validation |
| `KeycloakIdentityProviderFactory.java` | New `tenantQueryCache`; wired into `openSession()` and `clearCache()` |
| `KeycloakIdentityProviderSession.java` | New `tenantService`; `findTenantByQueryCriteria` / `findTenantCountByQueryCriteria`; user and group routing branches for `tenantId` |
| `KeycloakTenantQuery.java` | `executeList`/`executeCount` now delegate to session instead of returning empty |
| `KeycloakUserQuery.java` | `memberOfTenant()` no longer throws — sets `tenantId` on query |
| `KeycloakGroupQuery.java` | `memberOfTenant()` no longer throws — sets `tenantId` on query |
| `KeycloakUserService.java` | `requestUsersByTenantId()`; `transformUser` accepts `first_name`/`last_name` fallback |
| `KeycloakGroupService.java` | `requestGroupsByTenantId()` |
| `CacheableKeycloakUserQuery.java` | `tenantId` added to cache key, `equals`, and `hashCode` |
| `util/KeycloakPluginLogger.java` | `tenantQueryResult()` log code `052` |
| `json/JsonUtil.java` | `getJsonString()` returns `null` for explicit `JsonNull` elements (Referential Manager can return `null` fields) |

---

## Build and publishing changes vs upstream

| Aspect | Upstream | This fork |
|---|---|---|
| Maven `groupId` | `org.camunda.bpm.extension` | `com.openmairie.extension` |
| Distribution | Camunda Nexus + Sonatype Central | GitHub Packages (`maven.pkg.github.com/mairie-golfe-3/camunda-platform-7-keycloak`) |
| CI — build/test | `build.yml` runs on every PR/push with a Keycloak service container | **Removed.** No automated build CI. Run tests locally before pushing. |
| CI — publish | `deploy.yml` (Nexus + Central) | `on-release.yml` — publishes on GitHub Release publication using `secrets.DG_PAT`, Java 21 |

---

## Known issues / technical debt

- **Authorization check uses wrong resource type.** `KeycloakTenantService.isValid` checks
  `Permissions.READ` on `Resources.GROUP` (not `TENANT`). This is a copy from the group service.
  Revisit if per-tenant Camunda authorization is required.

- **No automated test coverage for tenant queries.** The integration test suite does not include
  tenant-specific tests. The Referential Manager is not started as part of the test setup. Tenant
  paths are exercised only manually.

- **`ReferentialTenant.java` is dead code.** The DTO was introduced in an early WIP but is not
  referenced anywhere. It can be removed.

- **No pagination in tenant results.** `KeycloakTenantService.postProcessResults` applies
  `maxResultSize` but does not sort. The TODO comment is still in place.

- **`referentialManagerUrl` is `protected` on `KeycloakConfiguration`** — not `private` like
  other fields. Likely intentional for subclassing but inconsistent.

- **No connect/read timeout on Referential Manager calls.** The `KeycloakRestTemplate` is shared
  with Keycloak Admin API calls; no per-request timeout is configured for RM endpoints. A slow or
  unavailable RM will block Camunda identity SPI threads. Fix: introduce a separate `RestTemplate`
  for RM calls with explicit timeouts and a retry policy.

- **Cross-audience token forwarding.** `KeycloakRestTemplate.exchange` injects the Keycloak
  admin bearer token on every call, including to the Referential Manager. The RM receives a token
  issued for Keycloak's Admin API. Fix: use a separate HTTP client for RM calls with its own auth
  strategy (dedicated client-credentials grant with RM as audience, mTLS, or API key).
