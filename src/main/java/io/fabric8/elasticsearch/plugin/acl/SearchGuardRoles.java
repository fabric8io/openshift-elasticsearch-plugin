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

import static io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.getUsernameHash;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices.Type;

public class SearchGuardRoles
        implements Iterable<SearchGuardRoles.Roles>, ConfigurationSettings, SearchGuardACLDocument<SearchGuardRoles> {

    public static final String ROLE_PREFIX = "gen";
    public static final String PROJECT_PREFIX = ROLE_PREFIX + "_project";
    public static final String USER_PREFIX = ROLE_PREFIX + "_user";

    private static final String CLUSTER_HEADER = "cluster";
    private static final String INDICES_HEADER = "indices";

    private List<Roles> roles = new ArrayList<>();

    public static class Roles {

        private String name;

        // This is just a list of actions
        private List<String> cluster;

        private List<Indices> indices;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getCluster() {
            return cluster;
        }

        public void setCluster(List<String> cluster) {
            this.cluster = cluster;
        }

        public List<Indices> getIndices() {
            return indices;
        }

        public void setIndices(List<Indices> indices) {
            this.indices = indices;
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
        }

        public static class Indices {

            private String index;

            private List<Type> types;

            public String getIndex() {
                return index;
            }

            public void setIndex(String index) {
                this.index = index;
            }

            public List<Type> getTypes() {
                return types;
            }

            public void setTypes(List<Type> types) {
                this.types = types;
            }

            public static class Type {

                private String type;

                private List<String> actions;

                public String getType() {
                    return type;
                }

                public void setType(String type) {
                    this.type = type;
                }

                public List<String> getActions() {
                    return actions;
                }

                public void setActions(List<String> actions) {
                    this.actions = actions;
                }

                @Override
                public String toString() {
                    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
                }
            }

            @Override
            public String toString() {
                return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
            }
        }
    }

    @Override
    public Iterator<Roles> iterator() {
        return new ArrayList<>(roles).iterator();
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    public void removeRole(Roles role) {
        roles.remove(role);
    }

    public void addAll(Collection<Roles> roles) {
        this.roles.addAll(roles);
    }

    public static String formatUniqueKibanaRoleName(String username) {
        return String.format("%s_%s_%s", ROLE_PREFIX, "kibana", getUsernameHash(username));
    }

    @SuppressWarnings("unchecked")
    public SearchGuardRoles load(Map<String, Object> source) {

        RolesBuilder builder = new RolesBuilder();

        for (String key : source.keySet()) {
            RoleBuilder roleBuilder = new RoleBuilder(key);

            // get out cluster and indices
            Map<String, Object> role = (Map<String, Object>) source.get(key);

            List<String> cluster = (List<String>) ObjectUtils.defaultIfNull(role.get(CLUSTER_HEADER), Collections.EMPTY_LIST);
            roleBuilder.setClusters(cluster);

            Map<String, Map<String, List<String>>> indices = (Map<String, Map<String, List<String>>>) ObjectUtils
                    .defaultIfNull(role.get(INDICES_HEADER), new HashMap<>());

            for (String index : indices.keySet()) {
                for (String type : indices.get(index).keySet()) {
                    List<String> actions = indices.get(index).get(type);
                    roleBuilder.setActions(index, type, actions);
                }
            }

            builder.addRole(roleBuilder.build());
        }

        roles = builder.build();
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> output = new TreeMap<String, Object>();

        // output keys are names of roles
        for (Roles role : roles) {
            Map<String, Object> roleObject = new TreeMap<String, Object>();

            Map<String, Object> indexObject = new TreeMap<String, Object>();
            for (Indices index : role.getIndices()) {

                Map<String, List<String>> typeObject = new TreeMap<String, List<String>>();
                for (Type type : index.getTypes()) {
                    typeObject.put(type.getType(), type.getActions());
                }

                indexObject.put(index.getIndex(), typeObject);
            }

            if (!indexObject.isEmpty()) {
                roleObject.put(INDICES_HEADER, indexObject);
            }
            if (!role.getCluster().isEmpty()) {
                roleObject.put(CLUSTER_HEADER, role.getCluster());
            }

            output.put(role.getName(), roleObject);
        }

        return output;
    }

    @Override
    public String getType() {
        return ConfigurationSettings.SEARCHGUARD_ROLE_TYPE;
    }

    public XContentBuilder toXContentBuilder() {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            builder.map(toMap());
            return builder;
        } catch (IOException e) {
            throw new RuntimeException("Unable to convert the SearchGuardRoles to JSON", e);
        }
    }

}
