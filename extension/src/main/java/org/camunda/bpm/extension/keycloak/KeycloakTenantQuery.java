package org.camunda.bpm.extension.keycloak;

import java.util.Collections;
import java.util.List;

import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.impl.Page;
import org.camunda.bpm.engine.impl.TenantQueryImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.persistence.entity.TenantEntity;

/**
 * Since multi-tenancy is currently not yet supported for the Keycloak plugin, the query always
 * returns <code>0</code> or an empty list.
 */
public class KeycloakTenantQuery extends TenantQueryImpl {

  private static final long serialVersionUID = 1L;

  public KeycloakTenantQuery() {
    super();
  }

  public KeycloakTenantQuery(CommandExecutor commandExecutor) {
    super(commandExecutor);
  }

  @Override
  public long executeCount(CommandContext commandContext) {
    final KeycloakIdentityProviderSession identityProvider = getKeycloakIdentityProvider(commandContext);
    return identityProvider.findTenantCountByQueryCriteria(this);
  }

  @Override
  public List<Tenant> executeList(CommandContext commandContext, Page page) {
    final KeycloakIdentityProviderSession identityProvider = getKeycloakIdentityProvider(commandContext);
    return identityProvider.findTenantByQueryCriteria(this);
  }

  protected KeycloakIdentityProviderSession getKeycloakIdentityProvider(CommandContext commandContext) {
    return (KeycloakIdentityProviderSession) commandContext.getReadOnlyIdentityProvider();
  }

}
