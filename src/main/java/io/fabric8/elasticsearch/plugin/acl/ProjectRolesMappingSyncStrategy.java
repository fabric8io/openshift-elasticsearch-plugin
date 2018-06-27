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

import static io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.PROJECT_PREFIX;

import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;

public class ProjectRolesMappingSyncStrategy extends BaseRolesMappingSyncStrategy {

    public ProjectRolesMappingSyncStrategy(SearchGuardRolesMapping rolesMapping, long expiresInMillis) {
        super(rolesMapping, expiresInMillis);
    }
    
    @Override
    protected  void syncFromImpl(OpenshiftRequestContext context, RolesMappingBuilder builder) {
        
        final String username = context.getUser();
        for (String project : context.getProjects()) {
            String projectRoleName = String.format("%s_%s", PROJECT_PREFIX, project.replace('.', '_'));

            builder.addUser(projectRoleName, username);
        }

        if (context.isOperationsUser()) {
            builder.addUser(SearchGuardRolesMapping.ADMIN_ROLE, username);
            builder.addUser(SearchGuardRolesMapping.KIBANA_SHARED_ROLE, username);
        } else {
            //role mapping for user's kibana index
            String kibanaRoleName = SearchGuardRoles.formatUniqueKibanaRoleName(username);
            builder.addUser(kibanaRoleName, username).expire(getExpires());
        }
    }
}
