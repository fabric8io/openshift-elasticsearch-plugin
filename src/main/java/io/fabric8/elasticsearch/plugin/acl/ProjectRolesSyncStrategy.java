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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

/**
 * SearchGuard Roles Document sync strategy based on roles 
 * derived from projects.  This should generate role mappings like:
 * 
 * gen_kibana_a1881c06eec96db9901c7bbfe41c42a3f08e9cb4:
 *   users: [user2]
 * gen_ocp_kibana_shared:
 *   users: [user1, user3]
 *  gen_project_foo_bar:
 *   users: [user2]
 * gen_project_operations:
 *   users: [user1, user3]
 * 
 */
public class ProjectRolesSyncStrategy extends BaseRolesSyncStrategy {


    private final String cdmProjectPrefix;
    private final String kibanaIndexMode;
    
    public ProjectRolesSyncStrategy(SearchGuardRoles roles, final String userProfilePrefix, final String cdmProjectPrefix, final String kibanaIndexMode) {
        super(roles, userProfilePrefix);
        this.roles = roles;
        this.cdmProjectPrefix = cdmProjectPrefix;
        this.kibanaIndexMode = kibanaIndexMode;
    }

    @Override
    public void syncFromImpl(UserProjectCache cache, RolesBuilder builder) {
        for (String project : cache.getAllProjects()) {
            String projectName = String.format("%s_%s", SearchGuardRoles.PROJECT_PREFIX, project.replace('.', '_'));
            String indexName = String.format("%s?*", project.replace('.', '?'));
            RoleBuilder role = new RoleBuilder(projectName).setActions(indexName, ALL,
                    PROJECT_ROLE_ACTIONS);

            // If using common data model, allow access to both the
            // $projname.$uuid.* indices and
            // the project.$projname.$uuid.* indices for backwards compatibility
            if (StringUtils.isNotEmpty(cdmProjectPrefix)) {
                indexName = String.format("%s?%s?*", cdmProjectPrefix.replace('.', '?'), project.replace('.', '?'));
                role.setActions(indexName, ALL, PROJECT_ROLE_ACTIONS);
            }

            builder.addRole(role.build());
        }
        
        boolean foundAnOpsUser = false;
        //create roles for every user we know about to their kibana index
        for (Map.Entry<SimpleImmutableEntry<String, String>, Set<String>> userToProjects : cache.getUserProjects()
                .entrySet()) {
            String username = userToProjects.getKey().getKey();
            String token = userToProjects.getKey().getValue();
            
            String roleName = formatKibanaRoleName(cache, username, token);
            String indexName = formatKibanaIndexName(cache, username, token, kibanaIndexMode);

            RoleBuilder role = new RoleBuilder(roleName)
                    .setActions(indexName, ALL, KIBANA_ROLE_INDEX_ACTIONS);
            if (cache.isOperationsUser(username, token)) {
                foundAnOpsUser = true;
                role.setClusters(KIBANA_ROLE_CLUSTER_ACTIONS)
                    .setActions(ALL, ALL, KIBANA_ROLE_ALL_INDEX_ACTIONS);
            }
            builder.addRole(role.build());
        }
        if (foundAnOpsUser) {
            RoleBuilder opsRole = new RoleBuilder(SearchGuardRolesMapping.ADMIN_ROLE)
                    .setClusters(OPERATIONS_ROLE_CLUSTER_ACTIONS)
                    .setActions("?operations?", ALL, OPERATIONS_ROLE_OPERATIONS_ACTIONS)
                    .setActions("*?*?*", ALL, OPERATIONS_ROLE_ANY_ACTIONS);
            builder.addRole(opsRole.build());
        }
    }
}
