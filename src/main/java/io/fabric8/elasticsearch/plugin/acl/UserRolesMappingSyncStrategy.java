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

import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;

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
 * gen_kibana_user2:
 *   users: [user2]
 */
public class UserRolesMappingSyncStrategy extends BaseRolesMappingSyncStrategy {

    public UserRolesMappingSyncStrategy(SearchGuardRolesMapping mapping, long expiresInMillis) {
        super(mapping, expiresInMillis);
    }

    @Override
    protected void syncFromImpl(OpenshiftRequestContext context, RolesMappingBuilder builder) {

        final String user = context.getUser();
        String kibanaRoleName = BaseRolesSyncStrategy.formatKibanaRoleName(context);
        builder.addUser(kibanaRoleName, user)
            .expire(getExpires());
        if (context.isOperationsUser()) {
            builder.addUser(SearchGuardRolesMapping.ADMIN_ROLE, user)
                .expire(getExpires());
        } else {
            String roleName = BaseRolesSyncStrategy.formatUserRoleName(user);
            builder.addUser(roleName, user).expire(getExpires());
        }
    }

}
