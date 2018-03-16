/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fabric8.elasticsearch.plugin.acl;

import org.apache.commons.lang.StringUtils;

import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;

/**
 * SearchGuard Roles Document sync strategy based on roles 
 * derived from projects.  This should generate role mappings like:
 * 
  gen_project_foo_bar:
    indices:
      ?foo?bar?*:
        '*': [INDEX_PROJECT]
      project?foo?bar?*:
        '*': [INDEX_PROJECT]
 * 
 */
public class ProjectRolesSyncStrategy extends BaseRolesSyncStrategy {


    private final String cdmProjectPrefix;
    private final String kibanaIndexMode;
    private final long expires;
    
    public ProjectRolesSyncStrategy(SearchGuardRoles roles, 
            final String userProfilePrefix, final String cdmProjectPrefix, final String kibanaIndexMode, final long expiresInMillies) {
        super(roles, userProfilePrefix);
        this.roles = roles;
        this.cdmProjectPrefix = cdmProjectPrefix;
        this.kibanaIndexMode = kibanaIndexMode;
        this.expires = expiresInMillies;
    }

    @Override
    public void syncFromImpl(OpenshiftRequestContext context, RolesBuilder builder) {
        for (String project : context.getProjects()) {
            String projectName = String.format("%s_%s", SearchGuardRoles.PROJECT_PREFIX, project.replace('.', '_'));
            String indexName = String.format("%s?*", project.replace('.', '?'));
            RoleBuilder role = new RoleBuilder(projectName).setActions(indexName, ALL,
                    PROJECT_ROLE_ACTIONS);
            role.expires(expires);

            // If using common data model, allow access to both the
            // $projname.$uuid.* indices and
            // the project.$projname.$uuid.* indices for backwards compatibility
            if (StringUtils.isNotEmpty(cdmProjectPrefix)) {
                indexName = String.format("%s?%s?*", cdmProjectPrefix.replace('.', '?'), project.replace('.', '?'));
                role.setActions(indexName, ALL, PROJECT_ROLE_ACTIONS);
            }

            builder.addRole(role.build());
        }
        
        //create role to user's Kibana index
        String kibanaRoleName = formatKibanaRoleName(context);
        String kibanaIndexName = formatKibanaIndexName(context, kibanaIndexMode);
        RoleBuilder kibanaRole = new RoleBuilder(kibanaRoleName)
                .setActions(kibanaIndexName, ALL, KIBANA_ROLE_INDEX_ACTIONS);
        if (context.isOperationsUser()) {
            kibanaRole.setClusters(KIBANA_ROLE_CLUSTER_ACTIONS)
                .setActions(ALL, ALL, KIBANA_ROLE_ALL_INDEX_ACTIONS);
        }else {
            kibanaRole.expires(expires);
        }
        builder.addRole(kibanaRole.build());

        //statically add to roles?
        if (context.isOperationsUser()) {
            
            builder.addRole(kibanaRole.build());
            RoleBuilder opsRole = new RoleBuilder(SearchGuardRolesMapping.ADMIN_ROLE)
                    .setClusters(OPERATIONS_ROLE_CLUSTER_ACTIONS)
                    .setActions("?operations?", ALL, OPERATIONS_ROLE_OPERATIONS_ACTIONS)
                    .setActions("*?*?*", ALL, OPERATIONS_ROLE_ANY_ACTIONS);
            builder.addRole(opsRole.build());
        }
    }
}
