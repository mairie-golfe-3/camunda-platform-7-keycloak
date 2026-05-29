# AGENTS.md

## Project overview

Camunda Platform 7 Keycloak Identity Provider plugin — bridges Camunda 7 with Keycloak for read-only user/group identity and SSO. **Camunda 7 CE reached EOL October 2025; this repo is deprecated at 7.24.0.**

- Maven multi-module, Java 17, Spring Boot 3.x
- No Gradle, no Makefile, no task runner — Maven only

## Modules

| Module | Purpose |
|---|---|
| `extension` | Core plugin JAR — main source of truth |
| `extension-run` | Shaded/fat JAR for Camunda Platform Run (packages relocated to `keycloakjar.*`) |
| `extension-jwt` | JWT-based auth addon (depends on `spring-boot-starter-oauth2-resource-server`) |
| `extension-all` | Assembly artifact |
| `examples/` | Sub-modules: `sso-kubernetes`, `jwt`, `run`, `tomcat`, `wildfly` |

## Key commands

```bash
# Start local Keycloak (required before running tests)
docker compose up -d

# Full build + integration tests
mvn -B clean install

# Build without tests
mvn -B clean install -DskipTests

# Build core modules only (skip examples)
mvn -B clean install -pl extension,extension-run,extension-jwt,extension-all

# License header check (also runs in CI)
mvn com.mycila:license-maven-plugin:check
```

## Tests require a live Keycloak

All tests under `extension/src/test/` are **integration tests** — they hit a real Keycloak REST API. There are no unit tests with mocks.

- The test base class provisions a Keycloak realm (users, groups) on startup via Admin REST API.
- Controlled via environment variables:

| Variable | Default |
|---|---|
| `KEYCLOAK_URL` | `http://localhost:8080/auth` |
| `KEYCLOAK_ADMIN_USER` | `keycloak` |
| `KEYCLOAK_ADMIN_PASSWORD` | `keycloak1!` |
| `KEYCLOAK_ENFORCE_SUBGROUPS_IN_GROUP_QUERY` | `true` |

- Local `docker-compose.yml` starts Keycloak 26 on port 8080 with those exact credentials.
- CI uses `quay.io/keycloak/keycloak:19.0.3-legacy` on port 8443 with `KEYCLOAK_ENFORCE_SUBGROUPS_IN_GROUP_QUERY=false`.

## Known gotchas

- **Do not** configure `camunda.bpm.admin-user` — the plugin is read-only; doing so causes startup errors.
- The Spring Security `user-name-attribute` must match the plugin flags `useEmailAsCamundaUserId` / `useUsernameAsCamundaUserId`.
- Keycloak >= 23: set `enforceSubgroupsInGroupQuery: true`.
- Keycloak Quarkus distribution (>= 17): no `/auth` context path — adjust `KEYCLOAK_URL` accordingly.
- `extension-run` shades packages under `keycloakjar.*` to avoid classpath conflicts in Camunda Run.

## Fork-specific: multi-tenancy

This fork adds tenant support not present in upstream. See **[doc/adr-multi-tenant.md](openmairie-docs/adr-multi-tenant.md)** for full design, HTTP contract, code map, and known issues.

Key facts for working in this fork:
- `referentialManagerUrl` is **mandatory** — plugin fails to start without it.
- `memberOfTenant()` on user/group queries is **no longer unsupported** — it routes to the Referential Manager.
- All test `camunda.*.cfg.xml` files must include `referentialManagerUrl` (already done for existing tests; add it when creating new test configs).
- There is **no automated PR/push CI** in this fork — run `docker compose up -d && mvn -B clean install` locally before pushing.

## CI

- `on-release.yml`: publishes to GitHub Packages on GitHub Release publication using `secrets.DG_PAT`, Java 21. **No automated PR/push CI exists in this fork.**
- Dependency updates: managed by Renovate (`config:recommended`).
