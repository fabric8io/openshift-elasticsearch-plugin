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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.Set;

public class ProjectRolesMappingSyncStrategy extends BaseRolesMappingSyncStrategy {

    public ProjectRolesMappingSyncStrategy(SearchGuardRolesMapping rolesMapping) {
        super(rolesMapping);
    }
    
    @Override
    protected  void syncFromImpl(UserProjectCache cache, RolesMappingBuilder builder) {
        for (Entry<SimpleImmutableEntry<String, String>, Set<String>> userProjects : cache.getUserProjects()
                .entrySet()) {
            String username = userProjects.getKey().getKey();
            String token = userProjects.getKey().getValue();

            for (String project : userProjects.getValue()) {
                String projectRoleName = String.format("%s_%s", PROJECT_PREFIX, project.replace('.', '_'));

                builder.addUser(projectRoleName, username);
            }

            if (cache.isOperationsUser(username, token)) {
                builder.addUser(SearchGuardRolesMapping.ADMIN_ROLE, username);
                builder.addUser(SearchGuardRolesMapping.KIBANA_SHARED_ROLE, username);
            } else {
                //role mapping for user's kibana index
                String kibanaRoleName = SearchGuardRoles.formatUniqueKibanaRoleName(username);
                builder.addUser(kibanaRoleName, username);
            }
        }
    }
}
