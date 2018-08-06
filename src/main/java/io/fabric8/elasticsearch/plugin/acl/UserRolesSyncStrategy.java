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

public class UserRolesSyncStrategy extends BaseRolesSyncStrategy implements RolesSyncStrategy {

    private final String cdmProjectPrefix;
    private final String kibanaIndexMode;
    private String expire;

    public UserRolesSyncStrategy(SearchGuardRoles roles, String userProfilePrefix, String cdmProjectPrefix, String kibanaIndexMode, long expiresInMillis) {
        super(roles, userProfilePrefix);
        this.cdmProjectPrefix = cdmProjectPrefix;
        this.kibanaIndexMode = kibanaIndexMode;
        this.expire = String.valueOf(expiresInMillis);
    }

    protected void syncFromImpl(OpenshiftRequestContext context, RolesBuilder builder) {
        String kibIndexName = formatKibanaIndexName(context, kibanaIndexMode);
        String kibRoleName = formatKibanaRoleName(context);
        RoleBuilder kibRole = new RoleBuilder(kibRoleName)
                .setClusterActions(KIBANA_ROLE_CLUSTER_ACTIONS)
                .setActions(kibIndexName, ALL, KIBANA_ROLE_INDEX_ACTIONS)
                .expires(expire);
        builder.addRole(kibRole.build());

        //think this can be statically added to roles doc
        if (context.isOperationsUser()) {
            RoleBuilder opsRole = new RoleBuilder(SearchGuardRolesMapping.ADMIN_ROLE)
                    .setClusters(OPERATIONS_ROLE_CLUSTER_ACTIONS)
                    .setActions(ALL, ALL, OPERATIONS_ROLE_ANY_ACTIONS)
                    .expires(expire);
            builder.addRole(opsRole.build());
            return;
        }        

        String roleName = formatUserRoleName(context.getUser());
        RoleBuilder role = new RoleBuilder(roleName)
                .setClusters(USER_ROLE_CLUSTER_ACTIONS)
                .expires(expire);

        //permissions for projects
        for (String project : context.getProjects()) {
            String indexName = String.format("%s?*", project.replace('.', '?'));
            role.setActions(indexName, ALL, PROJECT_ROLE_ACTIONS);
            // If using common data model, allow access to both the
            // $projname.$uuid.* indices and
            // the project.$projname.$uuid.* indices for backwards compatibility
            if (StringUtils.isNotEmpty(cdmProjectPrefix)) {
                indexName = String.format("%s?%s?*", cdmProjectPrefix.replace('.', '?'), project.replace('.', '?'));
                role.setActions(indexName, ALL, PROJECT_ROLE_ACTIONS);
            }
        }
        builder.addRole(role.build());
        
    }
}