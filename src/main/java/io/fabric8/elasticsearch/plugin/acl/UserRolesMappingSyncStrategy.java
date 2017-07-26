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
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

/**
 * SearchGuard Roles Document sync strategy based on roles 
 * derived from users.  This should generate role mappings like:
 * 
 * gen_project_operations:
 *   users: [user1, user3]
 * gen_ocp_kibana_shared:
 *   users: [user1, user3]
 * gen_user_user2:
 *   users: [user2]
 */
public class UserRolesMappingSyncStrategy extends BaseRolesMappingSyncStrategy {

    public UserRolesMappingSyncStrategy(SearchGuardRolesMapping mapping) {
        super(mapping);
    }

    @Override
    protected void syncFromImpl(UserProjectCache cache, RolesMappingBuilder builder) {
        Set<String> opsUsers = new HashSet<>();
        for (Entry<SimpleImmutableEntry<String, String>, Set<String>> userProjects : cache.getUserProjects()
                .entrySet()) {
            String username = userProjects.getKey().getKey();
            String token = userProjects.getKey().getValue();

            if (cache.isOperationsUser(username, token)) {
                opsUsers.add(username);
            } else {
                String roleName = BaseRolesSyncStrategy.formatUserRoleName(username);
                builder.addUser(roleName, username);
            }
        }
        for (String user : opsUsers) {
            builder.addUser(SearchGuardRolesMapping.ADMIN_ROLE, user);
            builder.addUser(SearchGuardRolesMapping.KIBANA_SHARED_ROLE, user);
        }
    }

}
