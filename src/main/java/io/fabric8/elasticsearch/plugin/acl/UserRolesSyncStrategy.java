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


public class UserRolesSyncStrategy extends BaseRolesSyncStrategy implements RolesSyncStrategy {

    private final String cdmProjectPrefix;
    private final String kibanaIndexMode;

    public UserRolesSyncStrategy(SearchGuardRoles roles, String userProfilePrefix, String cdmProjectPrefix, String kibanaIndexMode) {
        super(roles, userProfilePrefix);
        this.cdmProjectPrefix = cdmProjectPrefix;
        this.kibanaIndexMode = kibanaIndexMode;
    }

    protected void syncFromImpl(UserProjectCache cache, RolesBuilder builder) {
        boolean foundAnOpsUser = false;
        //create roles for every user we know about to their kibana index
        for (Map.Entry<SimpleImmutableEntry<String, String>, Set<String>> userToProjects : cache.getUserProjects()
                .entrySet()) {

            String user = userToProjects.getKey().getKey();
            String token = userToProjects.getKey().getValue();
            if (cache.isOperationsUser(user, token)) {
                foundAnOpsUser = true;
            } else {
                String roleName = formatUserRoleName(user);
                
                //permissions for kibana Index
                String kibIndexName = formatKibanaIndexName(cache, user, token, kibanaIndexMode);
                RoleBuilder role = new RoleBuilder(roleName)
                        .setClusters(USER_ROLE_CLUSTER_ACTIONS)
                        .setActions(kibIndexName, ALL, KIBANA_ROLE_INDEX_ACTIONS);
                
                //permissions for projects
                for (String project : userToProjects.getValue()) {
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
        if (foundAnOpsUser) {
            RoleBuilder opsRole = new RoleBuilder(SearchGuardRolesMapping.ADMIN_ROLE)
                    .setClusters(OPERATIONS_ROLE_CLUSTER_ACTIONS)
                    .setActions("?operations?", ALL, OPERATIONS_ROLE_OPERATIONS_ACTIONS)
                    .setActions("*?*?*", ALL, OPERATIONS_ROLE_ANY_ACTIONS);
            builder.addRole(opsRole.build());
            RoleBuilder kibanaOpsRole = new RoleBuilder(SearchGuardRolesMapping.KIBANA_SHARED_ROLE)
                    .setClusters(KIBANA_ROLE_CLUSTER_ACTIONS)
                    .setActions(ALL, ALL, KIBANA_ROLE_ALL_INDEX_ACTIONS);
            builder.addRole(kibanaOpsRole.build());
        }        
    }
}
