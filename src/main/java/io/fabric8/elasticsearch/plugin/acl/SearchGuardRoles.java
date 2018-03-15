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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.elasticsearch.common.xcontent.XContentBuilder;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices.Type;

public class SearchGuardRoles
        implements Iterable<SearchGuardRoles.Roles>, ConfigurationSettings, SearchGuardACLDocument<SearchGuardRoles> {

    public static final String ROLE_PREFIX = "gen";
    public static final String PROJECT_PREFIX = ROLE_PREFIX + "_project";
    public static final String USER_PREFIX = ROLE_PREFIX + "_user";
    public static final String USER_KIBANA_PREFIX = ROLE_PREFIX + "_kibana";

    private static final String CLUSTER_HEADER = "cluster";
    private static final String INDICES_HEADER = "indices";

    private Map<String, Roles> roles = new HashMap<>();
    private Long version;

    public static class Roles {

        private String name;
        private String expires;
        
        // This is just a list of actions
        private List<String> cluster = new ArrayList<>();

        private List<Indices> indices = new ArrayList<>();
        
        public Roles() {
        }

        public Roles(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
        
        public void setExpires(String expiresInMillies) {
            this.expires = expiresInMillies;
        }
        
        public String getExpire() {
            return expires;
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

        public void addClusterAction(String action) {
            this.cluster.add(action);
        }
        
        public void addIndexAction(Indices index) {
            this.indices.add(index);
        }

        public void addIndexAction(String index, String type, String action) {
            this.indices.add(new Indices(index, type, action));
        }

        public List<Indices> getIndices() {
            return indices;
        }

        public void setIndices(List<Indices> indices) {
            this.indices = indices;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("name=").append(getName()).append("\n")
                    .append("expire=").append(getExpire()).append("\n")
                    .toString();
        }

        public static class Indices {
            
            public Indices() {
            }

            public Indices(String index, String type, String action) {
                setIndex(index);
                setTypes(Arrays.asList(new Type(type, action)));
            }
            
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

                public Type() {
                }
                
                public Type(String type, String action) {
                    this.type = type;
                    this.actions = Arrays.asList(action);
                }
                
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

    public SearchGuardRoles() {
    }
    
    public SearchGuardRoles(Long version) {
        if(version != null && version.longValue() >= 0) {
            this.version = version;
        }
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public Iterator<Roles> iterator() {
        return new ArrayList<>(roles.values()).iterator();
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    public void removeRole(Roles role) {
        roles.remove(role.getName());
    }

    public void addAll(Collection<Roles> roles) {
        for (Roles role : roles) {
            this.roles.put(role.getName(), role);
        }
    }

    public static String formatUniqueKibanaRoleName(String username) {
        return String.format("%s_%s_%s", ROLE_PREFIX, "kibana", getUsernameHash(username));
    }

    @SuppressWarnings("unchecked")
    public SearchGuardRoles load(Map<String, Object> source) {
        if(source == null) {
            return this;
        }
        
        RolesBuilder builder = new RolesBuilder();

        for (String key : source.keySet()) {
            RoleBuilder roleBuilder = new RoleBuilder(key);

            // get out cluster and indices
            Map<String, Object> role = (Map<String, Object>) source.get(key);

            List<String> cluster = (List<String>) ObjectUtils.defaultIfNull(role.get(CLUSTER_HEADER), Collections.EMPTY_LIST);
            roleBuilder.setClusters(cluster);
            if(role.containsKey(EXPIRES)) {
                roleBuilder.expires((String)role.get(EXPIRES));
            }

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

        addAll(builder.build());
        return this;
    }

    @Override
    public String getType() {
        return ConfigurationSettings.SEARCHGUARD_ROLE_TYPE;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException{
        try {
            // output keys are names of roles
            for (Roles role : roles.values()) {
                builder.startObject(role.getName());
                if (!role.getCluster().isEmpty()) {
                    builder.array(CLUSTER_HEADER, role.getCluster().toArray());
                }
                if(role.getExpire() != null) {
                    builder.field(EXPIRES, role.getExpire());
                }
                if(!role.getIndices().isEmpty()) {
                    builder.startObject(INDICES_HEADER);
                    role.getIndices().sort(new Comparator<Indices>() {
                        @Override
                        public int compare(Indices o1, Indices o2) {
                            return o1.getIndex().compareTo(o2.getIndex());
                        }
                    });
                    for (Indices index : role.getIndices()) {
                        if(!index.getTypes().isEmpty()) {
                            builder.startObject(index.getIndex());
                            for (Type type : index.getTypes()) {
                                builder.array(type.getType(), type.getActions().toArray());
                            }
                            builder.endObject();
                        }
                    }
                    builder.endObject();
                }
                builder.endObject();
            }
            return builder;
        } catch (IOException e) {
            throw new RuntimeException("Unable to convert the SearchGuardRoles to JSON", e);
        }
    }

}
