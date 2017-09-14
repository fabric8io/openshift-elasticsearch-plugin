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

import static io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.USER_PREFIX;

import java.util.Iterator;

import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles;

public abstract class BaseRolesSyncStrategy implements RolesSyncStrategy {

    protected SearchGuardRoles roles;
    private final String userProfilePrefix;
   
    protected BaseRolesSyncStrategy(SearchGuardRoles roles, String userProfilePrefix) {
        this.roles = roles;
        this.userProfilePrefix = userProfilePrefix;

    }
    
    protected abstract void syncFromImpl(UserProjectCache cache, RolesBuilder builder);
    
    @Override
    public void syncFrom(UserProjectCache cache) {
        removeSyncAcls();
        RolesBuilder builder = new RolesBuilder();
        syncFromImpl(cache, builder);
        roles.addAll(builder.build());
    }

    // Remove roles that start with "gen_"
    private void removeSyncAcls() {
        for (Iterator<Roles> i = roles.iterator(); i.hasNext();) {
            Roles role = i.next();
            if (role.getName() != null && role.getName().startsWith(SearchGuardRoles.ROLE_PREFIX)) {
                roles.removeRole(role);
            }
        }
    }
    
    protected String formatKibanaIndexName(UserProjectCache cache, String username, String token, String kibanaIndexMode) {
        String kibanaIndex = OpenshiftRequestContextFactory.getKibanaIndex(userProfilePrefix, 
                kibanaIndexMode, username, cache.isOperationsUser(username, token));
        return kibanaIndex.replace('.','?');
    }
    
    
    protected String formatKibanaRoleName(UserProjectCache cache, String username, String token) {
        boolean isOperationsUser = cache.isOperationsUser(username, token);
        if (isOperationsUser) {
            return SearchGuardRolesMapping.KIBANA_SHARED_ROLE;
        } else {
            return SearchGuardRoles.formatUniqueKibanaRoleName(username);
        }
    }
    
    public static String formatUserRoleName(String username) {
        return String.format("%s_%s", USER_PREFIX, username.replaceAll("[\\\\.@/]", "_"));
    }

}
