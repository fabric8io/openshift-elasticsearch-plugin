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

import static io.fabric8.elasticsearch.plugin.KibanaUserReindexFilter.getUsernameHash;
import static io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.PROJECT_PREFIX;
import static io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.ROLE_PREFIX;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class SearchGuardRolesMapping 
	implements Iterable<SearchGuardRolesMapping.RolesMapping> {

	private static final String USER_HEADER = "users";
	private static final String ADMIN_ROLE = "sg_project_operations";
	private List<RolesMapping> mappings;
	
	public static class RolesMapping {
		
		private String name;
		
		private List<String> users = new ArrayList<String>();
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public List<String> getUsers() {
			return users;
		}
		
		public void setUsers(List<String> users) {
			this.users = users;
		}
		
		@Override
		public String toString() {
			return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
		}
	}

	@Override
	public Iterator<RolesMapping> iterator() {
		return new ArrayList<>(mappings).iterator();
	}
	
	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
	
	public void removeRolesMapping(RolesMapping mapping) {
		mappings.remove(mapping);
	}
	
	public void syncFrom(UserProjectCache cache, final String userProfilePrefix){
		removeSyncAcls();
		
		RolesMappingBuilder builder = new RolesMappingBuilder();
		
		for (Map.Entry<String, Set<String>> userProjects : cache.getUserProjects().entrySet()) {
			String username = userProjects.getKey();
			String usernameHash = getUsernameHash(username);
			String kibanaRoleName = String.format("%s_%s_%s", ROLE_PREFIX, "kibana", usernameHash);
			
			builder.addUser(kibanaRoleName, username);
			
			for ( String project : userProjects.getValue() ) {
				String projectRoleName = String.format("%s_%s", PROJECT_PREFIX, project.replace('.', '_'));
			
				builder.addUser(projectRoleName, username);
			}
			
			if ( cache.isOperationsUser(username) ) {
				builder.addUser(ADMIN_ROLE, username);
			}
		}
		
		mappings.addAll(builder.build());
	}
	
	// Remove roles that start with "gen_"
	private void removeSyncAcls() {
		for (RolesMapping mapping : new ArrayList<>(mappings)) {
			if(mapping.getName() != null && mapping.getName().startsWith(ROLE_PREFIX)){
				removeRolesMapping(mapping);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public SearchGuardRolesMapping load(Map<String, Object> source) {
		
		RolesMappingBuilder builder = new RolesMappingBuilder();
		
		for ( String key : source.keySet() ) {
			HashMap<String, List<String>> users = (HashMap<String, List<String>>) source.get(key);
			builder.setUsers(key, (ArrayList<String>)users.get(USER_HEADER));
		}
		
		mappings = builder.build();
		return this;
	}
	
	public Map<String, Object> toMap() {
		Map<String, Object> output = new HashMap<String, Object>();
		
		// output keys are names of mapping
		for ( RolesMapping mapping : mappings ) {
			Map<String, List<String>> mappingObject = new HashMap<String, List<String>>();
			
			mappingObject.put(USER_HEADER, mapping.getUsers());
			
			output.put(mapping.getName(), mappingObject);
		}
		
		return output;
	}
}
