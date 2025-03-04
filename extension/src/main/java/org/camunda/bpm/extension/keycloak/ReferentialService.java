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
            /*final ResponseEntity<List<ReferentialTenant>> response = restTemplate.exchange(
                    "https://ee9e1031-0d62-4ab8-8fbe-e5630ff26510.mock.pstmn.io/tenants",
                    HttpMethod.GET,
                    null,
                    tenantsResponse);

            if (response.getBody() == null) {
                return Collections.emptyList();
            }*/

            List<ReferentialTenant> referentialTenants = new ArrayList<>();
            referentialTenants.add(new ReferentialTenant("7ceb766f-62a9-4617-bb2d-871e43ac9602", "Mairie Golfe 3"));
            referentialTenants.add(new ReferentialTenant("7ceb766f-62a9-4617-bb2d-871e43a8941", "Mairie Plateau"));

            for (ReferentialTenant referentialTenant : referentialTenants) {
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
