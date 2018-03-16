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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.fabric8.elasticsearch.plugin.acl.SearchGuardRolesMapping.RolesMapping;

public class RolesMappingBuilder {

    private Map<String, HashSet<String>> roles = new HashMap<>();
    private List<RolesMapping> rolesMappings = new ArrayList<RolesMapping>();
    private Long expire;

    public List<RolesMapping> build() {

        for (String role : roles.keySet()) {
            RolesMapping mapping = new RolesMapping();
            mapping.setExpire(expire);
            mapping.setName(role);
            mapping.setUsers(new ArrayList<String>(roles.get(role)));

            rolesMappings.add(mapping);
        }

        return rolesMappings;
    }

    public RolesMappingBuilder addRole(String role) {
        roles.put(role, new HashSet<String>());
        return this;
    }

    public RolesMappingBuilder setUsers(String role, List<String> users) {
        roles.put(role, new HashSet<String>(users));
        return this;
    }

    public RolesMappingBuilder addUser(String role, String user) {
        if (!roles.containsKey(role)) {
            addRole(role);
        }

        roles.get(role).add(user);
        return this;
    }

    public RolesMappingBuilder expire(Long expire) {
        this.expire = expire;
        return this;
    }
    
}
