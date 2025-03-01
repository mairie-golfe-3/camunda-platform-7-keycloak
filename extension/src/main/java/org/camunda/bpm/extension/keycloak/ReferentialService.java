package org.camunda.bpm.extension.keycloak;

import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.impl.persistence.entity.TenantEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReferentialService {


    public ReferentialService() {
    }

    public List<Tenant> getTenants() {
        List<Tenant> tenants = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();
        final ParameterizedTypeReference<List<ReferentialTenant>> tenantsResponse = new ParameterizedTypeReference<List<ReferentialTenant>>() {};

        try {
            final ResponseEntity<List<ReferentialTenant>> response = restTemplate.exchange(
                    "http://localhost:8080/tenants",
                    HttpMethod.GET,
                    null,
                    tenantsResponse);

            if (response.getBody() == null) {
                return Collections.emptyList();
            }

            for (ReferentialTenant referentialTenant : response.getBody()) {
                TenantEntity tenant = new TenantEntity();
                tenant.setId(referentialTenant.getTenant_id());
                tenant.setName(referentialTenant.getName());
                tenants.add(tenant);
            }
        } catch (RestClientException e) {

            throw new RestClientException("Failed to retrieve tenants from referential service", e);

        }

        return tenants;

    }

    public long getTenantCount() {
        return getTenants().size();
    }
}
