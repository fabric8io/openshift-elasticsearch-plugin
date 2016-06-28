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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices.Type;

public class SearchGuardRoles 
	implements Iterable<SearchGuardRoles.Roles>, ConfigurationSettings {
	
	public final static String ROLE_PREFIX="gen";
	public final static String PROJECT_PREFIX=ROLE_PREFIX + "_project";
	private final static String[] DEFAULT_ROLE_ACTIONS={SG_ACTION_READ, "indices:admin/mappings/fields/get*", "indices:admin/validate/query*", "indices:admin/get*"};
	private final static String[] KIBANA_INDEX_ACTIONS={SG_ACTION_ALL};
	private final static String[] DEFAULT_CLUSTER_ACTIONS={"cluster:monitor/nodes/info", "cluster:monitor/health"};
	private final static String DEFAULT_ROLE_TYPE = "*";
	private final static String DEFAULT_ROLE_INDEX = "*";
	
	private final static String CLUSTER_HEADER = "cluster";
	private final static String INDICES_HEADER = "indices";

	private List<Roles> roles;
	
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
	
	public void syncFrom(UserProjectCache cache, final String userProfilePrefix){
		removeSyncAcls();
		
		RolesBuilder builder = new RolesBuilder();
		
		for (String project : cache.getAllProjects() ) {
			String projectName = String.format("%s_%s", PROJECT_PREFIX, project.replace('.', '_')); 
			String indexName = String.format("%s?*", project.replace('.', '?'));
			
			RoleBuilder role = new RoleBuilder(projectName)
					.setActions(indexName, DEFAULT_ROLE_TYPE, DEFAULT_ROLE_ACTIONS);
			
			builder.addRole(role.build());
		}
		
		for (Map.Entry<String, Set<String>> userProjects : cache.getUserProjects().entrySet()) {
			String usernameHash = getUsernameHash(userProjects.getKey());
			String projectName = String.format("%s_%s_%s", ROLE_PREFIX, "kibana", usernameHash); 
			String indexName = String.format("%s?%s", userProfilePrefix.replace('.', '?'), usernameHash);
			
			RoleBuilder role = new RoleBuilder(projectName)
					.setActions(indexName, DEFAULT_ROLE_TYPE, KIBANA_INDEX_ACTIONS)
					.setActions(DEFAULT_ROLE_INDEX, DEFAULT_ROLE_TYPE, DEFAULT_ROLE_ACTIONS)
					.setClusters(DEFAULT_CLUSTER_ACTIONS);
			
			builder.addRole(role.build());
		}
		
		roles.addAll(builder.build());
	}
	
	// Remove roles that start with "gen_"
	private void removeSyncAcls() {
		for (Roles role : new ArrayList<>(roles)) {
			if(role.getName() != null && role.getName().startsWith(ROLE_PREFIX)){
				removeRole(role);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public SearchGuardRoles load(Map<String, Object> source) {
		
		RolesBuilder builder = new RolesBuilder();
		
		for ( String key : source.keySet() ) {
			RoleBuilder roleBuilder = new RoleBuilder(key);
			
			// get out cluster and indices
			HashMap<String, Object> role = (HashMap<String, Object>) source.get(key);
			
			List<String> cluster = (ArrayList<String>)role.get(CLUSTER_HEADER);
			roleBuilder.setClusters(cluster);
			
			HashMap<String, HashMap<String, ArrayList<String>>> indices = (HashMap<String, HashMap<String, ArrayList<String>>>) role.get(INDICES_HEADER);
			for ( String index : indices.keySet() ) {
				for ( String type : indices.get(index).keySet() ) {
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
		Map<String, Object> output = new HashMap<String, Object>();
		
		// output keys are names of roles
		for ( Roles role : roles ) {
			Map<String, Object> roleObject = new HashMap<String, Object>();
			
			Map<String, Object> indexObject = new HashMap<String, Object>();
			for ( Indices index : role.getIndices() ) {

				Map<String, List<String>> typeObject = new HashMap<String, List<String>>();
				for ( Type type : index.getTypes() )
					typeObject.put(type.getType(), type.getActions());
				
				indexObject.put(index.getIndex(), typeObject);
			}
			
			roleObject.put(INDICES_HEADER, indexObject);
			roleObject.put(CLUSTER_HEADER, role.getCluster());
		
			output.put(role.getName(), roleObject);
		}
		
		return output;
	}
}
