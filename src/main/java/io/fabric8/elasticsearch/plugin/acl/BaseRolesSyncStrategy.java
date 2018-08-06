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

import static io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.USER_KIBANA_PREFIX;
import static io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.USER_PREFIX;

import io.fabric8.elasticsearch.plugin.KibanaIndexMode;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;

public abstract class BaseRolesSyncStrategy implements RolesSyncStrategy {

    protected SearchGuardRoles roles;
    private final String userProfilePrefix;

    protected BaseRolesSyncStrategy(SearchGuardRoles roles, String userProfilePrefix) {
        this.roles = roles;
        this.userProfilePrefix = userProfilePrefix;

    }

    protected abstract void syncFromImpl(OpenshiftRequestContext context, RolesBuilder builder);
    
    @Override
    public void syncFrom(OpenshiftRequestContext context) {
        RolesBuilder builder = new RolesBuilder();
        syncFromImpl(context, builder);
        roles.addAll(builder.build());
    }

    protected String formatKibanaIndexName(OpenshiftRequestContext context, String kibanaIndexMode) {
        String kibanaIndex = OpenshiftRequestContextFactory.getKibanaIndex(userProfilePrefix, 
                kibanaIndexMode, context.getUser(), context.isOperationsUser());
        return kibanaIndex.replace('.','?');
    }

    public static String formatKibanaRoleName(OpenshiftRequestContext context) {
        final String kibanaIndexMode = context.getKibanaIndexMode();
        if (context.isOperationsUser() ) {
            switch (kibanaIndexMode) {
            case KibanaIndexMode.UNIQUE:
                return SearchGuardRoles.formatUniqueKibanaRoleName(context.getUser());
            default:
                return SearchGuardRolesMapping.KIBANA_SHARED_ROLE;
            }
        }
        switch (kibanaIndexMode) {
        case KibanaIndexMode.SHARED_NON_OPS:
            return SearchGuardRolesMapping.KIBANA_SHARED_NON_OPS_ROLE;
        default:
            return SearchGuardRoles.formatUniqueKibanaRoleName(context.getUser());
        }
    }

    public static String formatUserRoleName(String username) {
        return String.format("%s_%s", USER_PREFIX, OpenshiftRequestContextFactory.getUsernameHash(username));
    }

    public static String formatUserKibanaRoleName(String username) {
        return String.format("%s_%s", USER_KIBANA_PREFIX, OpenshiftRequestContextFactory.getUsernameHash(username));
    }

}