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

import static io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.ROLE_PREFIX;

import java.util.Iterator;

import io.fabric8.elasticsearch.plugin.acl.SearchGuardRolesMapping.RolesMapping;

public abstract class BaseRolesMappingSyncStrategy implements RolesMappingSyncStrategy {

    protected final SearchGuardRolesMapping mappings;
    
    protected BaseRolesMappingSyncStrategy(final SearchGuardRolesMapping mappings) {
        this.mappings = mappings;
    }


    // Remove roles that start with "gen_"
    private void removeSyncAcls() {
        for (Iterator<RolesMapping> i = mappings.iterator(); i.hasNext();) {
            RolesMapping mapping = i.next();
            if (mapping.getName() != null && mapping.getName().startsWith(ROLE_PREFIX)) {
                mappings.removeRolesMapping(mapping);
            }
        }
    }


    protected abstract void syncFromImpl(UserProjectCache cache, RolesMappingBuilder builder);
    
    @Override
    public void syncFrom(UserProjectCache cache) {
        removeSyncAcls();
        RolesMappingBuilder builder = new RolesMappingBuilder();
        syncFromImpl(cache, builder);
        mappings.addAll(builder.build());
    }

    
}
