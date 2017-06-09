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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices.Type;

public class RoleBuilder {

    private Roles role = new Roles();
    private String name;
    private Set<String> clusters = new HashSet<String>();
    private Map<String, HashMap<String, HashSet<String>>> indices = new HashMap<String, HashMap<String, HashSet<String>>>();

    public RoleBuilder(String name) {
        this.name = name;
    }

    public RoleBuilder setClusters(List<String> clusters) {
        if (clusters != null) {
            this.clusters = new HashSet<String>(clusters);
        }
        return this;
    }

    public RoleBuilder setClusters(String[] clusters) {
        return setClusters(Arrays.asList(clusters));
    }

    public RoleBuilder addIndex(String index) {
        indices.put(index, new HashMap<String, HashSet<String>>());
        return this;
    }

    public RoleBuilder setActions(String index, String type, List<String> actions) {
        if (!indices.containsKey(index)) {
            addIndex(index);
        }

        indices.get(index).put(type, new HashSet<String>(actions));
        return this;
    }

    public RoleBuilder setActions(String index, String type, String[] actions) {
        return setActions(index, type, Arrays.asList(actions));
    }

    public Roles build() {
        role.setName(name);
        role.setCluster(new ArrayList<String>(clusters));

        List<Indices> roleIndices = new ArrayList<Indices>();
        for (String indexKey : indices.keySet()) {

            Indices index = new Indices();
            index.setIndex(indexKey);

            List<Type> types = new ArrayList<Type>();
            for (String typeKey : indices.get(indexKey).keySet()) {
                Type type = new Type();
                type.setType(typeKey);
                type.setActions(new ArrayList<String>(indices.get(indexKey).get(typeKey)));
                types.add(type);
            }

            index.setTypes(types);
            roleIndices.add(index);
        }

        role.setIndices(roleIndices);
        return role;
    }
}