package org.camunda.bpm.extension.keycloak;
import static org.camunda.bpm.engine.authorization.Permissions.READ;
import static org.camunda.bpm.engine.authorization.Resources.GROUP;
import static org.camunda.bpm.extension.keycloak.json.JsonUtil.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.impl.identity.IdentityProviderException;
import org.camunda.bpm.engine.impl.persistence.entity.TenantEntity;
import org.camunda.bpm.extension.keycloak.json.JsonException;
import org.camunda.bpm.extension.keycloak.rest.KeycloakRestTemplate;
import org.camunda.bpm.extension.keycloak.util.KeycloakPluginLogger;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KeycloakTenantService extends KeycloakServiceBase {
    /**
     * Default constructor.
     *
     * @param keycloakConfiguration   the Keycloak configuration
     * @param restTemplate            REST template
     * @param keycloakContextProvider Keycloak context provider
     */
    public KeycloakTenantService(KeycloakConfiguration keycloakConfiguration, KeycloakRestTemplate restTemplate, KeycloakContextProvider keycloakContextProvider) {
        super(keycloakConfiguration, restTemplate, keycloakContextProvider);
    }


    public List<Tenant> requestTenantsByUserId(CacheableKeycloakTenantQuery query) {
        String userId = query.getUserId();
        List<Tenant> tenants = new ArrayList<>();

        try {
            String keycloadID;
            try {
                keycloadID = getKeycloakUserID(userId);
            } catch (KeycloakUserNotFoundException e) {
                return Collections.emptyList();
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    "url",
                    HttpMethod.GET,
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IdentityProviderException("Unable to fetch tenants from "
                        + keycloakConfiguration.getKeycloakAdminUrl() + "for user " + userId +
                        ". HTTP Status code: " + response.getStatusCode());
            }

            JsonArray searchResult = parseAsJsonArray(response.getBody());
            for (int i = 0; i < searchResult.size(); i++) {
                tenants.add(transformTenant(getJsonObjectAtIndex(searchResult, i)));
            }
        } catch (HttpClientErrorException hcee) {
            // if userID is unknown server answers with HTTP 404 not found
            if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return Collections.emptyList();
            }
            throw hcee;
        } catch (Exception e) {
            throw new IdentityProviderException("Error while fetching tenants for user " + userId, e);
        }
        return tenants;
    }

    public List<Tenant> requestTenantsWithoutUserId(CacheableKeycloakTenantQuery query) {
        List<Tenant> tenants = new ArrayList<>();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "url",
                    HttpMethod.GET,
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IdentityProviderException("Unable to fetch tenants from "
                        + keycloakConfiguration.getKeycloakAdminUrl() +
                        ". HTTP Status code: " + response.getStatusCode());
            }

            JsonArray searchResult = parseAsJsonArray(response.getBody());
            for (int i = 0; i < searchResult.size(); i++) {
                tenants.add(transformTenant(getJsonObjectAtIndex(searchResult, i)));
            }
        } catch (HttpClientErrorException hcee) {
            // if userID is unknown server answers with HTTP 404 not found
            if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return Collections.emptyList();
            }
            throw hcee;
        } catch (Exception e) {
            throw new IdentityProviderException("Error while fetching tenants for user ");
        }
        return tenants;
    }

    private Tenant transformTenant(JsonObject jsonObject) throws JsonException {
        TenantEntity tenant = new TenantEntity();
        tenant.setId(getJsonString(jsonObject, "id"));
        tenant.setName(getJsonString(jsonObject, "name"));
        return tenant;
    }

    public List<Tenant> postProcessResults(KeycloakTenantQuery query, List<Tenant> tenants, StringBuilder resultLogger) {
        // client side check of further query filters
        Stream<Tenant> processed = tenants.stream().filter(tenant -> isValid(query, tenant, resultLogger));

        // TODO : Sort and paginate the result

        // group queries in Keycloak do not consider the max attribute within the search request
        return processed.limit(keycloakConfiguration.getMaxResultSize()).collect(Collectors.toList());

    }

    /**
     * Post processing query filter.
     * Checks if the given tenant matches the query.
     *
     * @param query        the query
     * @param tenant       the tenant
     * @param resultLogger the logger to log the result
     * @return true if the tenant matches the query, false otherwise
     */
    private boolean isValid(KeycloakTenantQuery query, Tenant tenant, StringBuilder resultLogger) {
        // client side check of further query filters
        if (!matches(query.getId(), tenant.getId())) return false;
        if (!matches(query.getIds(), tenant.getId())) return false;
        if (!matches(query.getName(), tenant.getName())) return false;
        if (!matchesLike(query.getNameLike(), tenant.getName())) return false;

        // authenticated user is always allowed to query his own groups
        // otherwise READ authentication is required
        boolean isAuthenticatedUser = isAuthenticatedUser(query.getUserId());
        if (isAuthenticatedUser || isAuthorized(READ, GROUP, tenant.getId())) {
            if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
                resultLogger.append(tenant);
                resultLogger.append(", ");
            }
            return true;
        }

        return false;
    }
}
