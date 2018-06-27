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

import org.elasticsearch.common.inject.Inject;

import io.fabric8.elasticsearch.plugin.PluginSettings;

/**
 * Factory class for creating SearchGuard sync strategies
 *
 */
public class SearchGuardSyncStrategyFactory {
    
    public static final String PROJECT = "project";
    public static final String USER = "user";
    
    private final PluginSettings settings;

    @Inject
    public SearchGuardSyncStrategyFactory(final PluginSettings settings) {
        this.settings = settings;
    }
    
    public RolesMappingSyncStrategy createRolesMappingSyncStrategy(SearchGuardRolesMapping mapping) {
        final long expires = System.currentTimeMillis() + settings.getACLExpiresInMillis();
        if(PROJECT.equals(settings.getRoleStrategy())) {
            return new ProjectRolesMappingSyncStrategy(mapping, expires);
        }
        return new UserRolesMappingSyncStrategy(mapping, expires);
    }
    
    public RolesSyncStrategy createRolesSyncStrategy(SearchGuardRoles roles) {
        final long expires = System.currentTimeMillis() + settings.getACLExpiresInMillis();
        if(PROJECT.equals(settings.getRoleStrategy())) {
            return new ProjectRolesSyncStrategy(roles, 
                    settings.getDefaultKibanaIndex(), settings.getCdmProjectPrefix(), settings.getKibanaIndexMode(), expires);
        }
        return new UserRolesSyncStrategy(roles, 
                settings.getDefaultKibanaIndex(), settings.getCdmProjectPrefix(), settings.getKibanaIndexMode(), expires);
    }
    
}
