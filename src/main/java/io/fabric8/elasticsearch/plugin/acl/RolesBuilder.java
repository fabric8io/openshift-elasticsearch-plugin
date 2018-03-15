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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices;

public class RolesBuilder {

    private Map<String, Roles> roles = new HashMap<>();

    public Collection<Roles> build() {
        return roles.values();
    }

    public RolesBuilder addRole(Roles role) {
        roles.put(role.getName(), role);
        return this;
    }
    
    public RoleBuilder newRoleBuilder(String name) {
        return new RoleBuilder(name);
    }
    
    public static class RoleBuilder {
        private Roles role = new Roles();
        
        RoleBuilder(String name){
            role.setName(name);
        }
        
        public RoleBuilder name(String name) {
            role.setName(name);
            return this;
        }

        public RoleBuilder expires(Long expiresInMillies) {
            role.setExpires(expiresInMillies);
            return this;
        }
        
        public RoleBuilder addClusterAction(String action) {
            role.addClusterAction(action);
            return this;
        }
        
        public RoleBuilder setClusterActions(List<String> actions) {
            role.setCluster(actions);
            return this;
        }
        
        public RoleBuilder addIndexAction(Indices action) {
            role.addIndexAction(action);
            return this;
        }
        
        public RoleBuilder addIndexAction(String index, String type, String action) {
            role.addIndexAction(index, type, action);
            return this;
        }
        
        public RoleBuilder setIndicesActions(List<Indices> actions) {
            role.setIndices(actions);
            return this;
        }
        
        public Roles build() {
            return role;
        }
        
    }
}
