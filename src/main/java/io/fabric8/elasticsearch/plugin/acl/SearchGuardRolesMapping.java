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

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;

public class SearchGuardRolesMapping implements Iterable<SearchGuardRolesMapping.RolesMapping>, SearchGuardACLDocument {

    public static final String ADMIN_ROLE = "gen_project_operations";
    public static final String KIBANA_SHARED_ROLE = SearchGuardRoles.ROLE_PREFIX + "_ocp_kibana_shared";
    private static final String USER_HEADER = "users";
    private Map<String, RolesMapping> mappings = new HashMap<>();
    private Long version;
    
    public static class RolesMapping {

        private Boolean protect;
        private String name;

        private Set<String> users = new HashSet<String>();

        private Long expire;

        public Boolean getProtected() {
            return this.protect;
        }
        
        public void setProtected(boolean protect) {
            this.protect = protect;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Collection<String> getUsers() {
            return users;
        }

        public void setUsers(Collection<String> users) {
            this.users = new HashSet<>(users);
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
        }

        public void addAll(Collection<String> users) {
            this.users.addAll(users);
        }

        public void setExpire(Long expire) {
            this.expire = expire;
        }

        public Long getExpire() {
            return this.expire;
        }
    }

    public SearchGuardRolesMapping() {
    }
    
    public SearchGuardRolesMapping(Long version) {
        if(version != null && version.longValue() >= 0) {
            this.version = version;
        }
    }
    
    public Long getVersion() {
        return version;
    }

    @Override
    public Iterator<RolesMapping> iterator() {
        return new ArrayList<>(mappings.values()).iterator();
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    public void removeRolesMapping(RolesMapping mapping) {
        mappings.remove(mapping.getName());
    }

    @SuppressWarnings("unchecked")
    public SearchGuardRolesMapping load(Map<String, Object> source) {
        if(source == null) {
            return this;
        }
        for (String key : source.keySet()) {
            Map<String, List<String>> rawMappings = (HashMap<String, List<String>>) source.get(key);

            RolesMapping mapping = new RolesMapping();
            mapping.setName(key);
            mapping.setUsers(new ArrayList<String>(rawMappings.get(USER_HEADER)));
            if(rawMappings.containsKey(EXPIRES)) {
                mapping.setExpire(((Number)rawMappings.get(EXPIRES)).longValue());
            }
            mappings.put(mapping.getName(), mapping);
        }
        
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> output = new TreeMap<String, Object>();

        // output keys are names of mapping
        for (RolesMapping mapping : mappings.values()) {
            Map<String, Object> mappingObject = new TreeMap<String, Object>();
            if(mapping.getExpire() != null) {
                mappingObject.put(EXPIRES, mapping.getExpire());
            }
            mappingObject.put(USER_HEADER, new ArrayList<>(mapping.getUsers()));
            
            output.put(mapping.getName(), mappingObject);
        }

        return output;
    }

    @Override
    public String getType() {
        return ConfigurationSettings.SEARCHGUARD_MAPPING_TYPE;
    }

    @Override
    public XContentBuilder toXContentBuilder() {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
            builder.map(toMap());
            return builder;
        } catch (IOException e) {
            throw new RuntimeException("Unable to convert the SearchGuardRolesMapping to JSON", e);
        }

    }

    public void addAll(Collection<RolesMapping> mappings) {
        for (RolesMapping rolesMapping : mappings) {
            if(this.mappings.containsKey(rolesMapping.getName())){
                this.mappings.get(rolesMapping.getName()).addAll(rolesMapping.getUsers());
            } else {
                this.mappings.put(rolesMapping.getName(), rolesMapping);
            }
        }
    }
}
