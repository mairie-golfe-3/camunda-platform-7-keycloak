package org.camunda.bpm.extension.keycloak;

import java.io.Serializable;

public class ReferentialTenant implements Serializable {

    private String tenant_id;

    private String name;

    public String getTenant_id() {
        return tenant_id;
    }

    public void setTenant_id(String tenant_id) {
        this.tenant_id = tenant_id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
