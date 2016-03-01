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

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import org.elasticsearch.common.settings.Settings;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Class representation of a ElasticSearch SearchGuard plugin ACL
 * 
 * @author jeff.cantrill
 *
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class SearchGuardACL implements Iterable<SearchGuardACL.Acl>, ConfigurationSettings {
	
	public static final String OPENSHIFT_SYNC = "[openshift-elasticsearch-plugin]";
	public static final String FLUENTD_USER = "system.logging.fluentd";
	public static final String KIBANA_USER = "system.logging.kibana";
	public static final String CURATOR_USER = "system.logging.curator";
	public static final String FLUENTD_EXECUTE_FILTER = "actionrequestfilter.fluentd";
	public static final String KIBANA_EXECUTE_FILTER = "actionrequestfilter.kibana";
	public static final String CURATOR_EXECUTE_FILTER = "actionrequestfilter.curator";
	public static final String KIBANA_INDEX = ".kibana.*";
	
	@JsonProperty(value="acl")
	private List<Acl> acls;
	
	public static class Acl {
		
		@JsonProperty(value="__Comment__")
		private String comment = "";
		
		@JsonProperty(value="hosts")
		private List<String> hosts = new ArrayList<>();
		
		@JsonProperty(value="users")
		private List<String> users = new ArrayList<>();
		
		@JsonProperty(value="roles")
		private List<String> roles = new ArrayList<>();
		
		@JsonProperty(value="indices")
		private List<String> indices = new ArrayList<>();
		
		@JsonProperty(value="aliases")
		private List<String> aliases = new ArrayList<>();
		
		@JsonProperty(value="filters_bypass")
		private List<String> filtersBypass = new ArrayList<>();

		@JsonProperty(value="filters_execute")
		private List<String> filtersExecute = new ArrayList<>();
		
		public String getComment() {
			return comment;
		}
		public void setComment(String comment) {
			this.comment = comment;
		}
		public List<String> getHosts() {
			return hosts;
		}
		public void setHosts(List<String> hosts) {
			this.hosts = hosts;
		}
		public List<String> getUsers() {
			return users;
		}
		public void setUsers(List<String> users) {
			this.users = users;
		}
		public List<String> getRoles() {
			return roles;
		}
		public void setRoles(List<String> roles) {
			this.roles = roles;
		}
		public List<String> getIndices() {
			return indices;
		}
		public void setIndices(List<String> indicies) {
			this.indices = indicies;
		}
		public List<String> getAliases() {
			return aliases;
		}
		public void setAliases(List<String> aliases) {
			this.aliases = aliases;
		}
		public List<String> getFiltersBypass() {
			return filtersBypass;
		}
		public void setFiltersBypass(List<String> filterBypass) {
			this.filtersBypass = filterBypass;
		}
		public List<String> getFiltersExecute() {
			return filtersExecute;
		}
		public void setFiltersExecute(List<String> filterExecute) {
			this.filtersExecute = filterExecute;
		}
		@Override
		public String toString() {
			return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
		}
		
		
	}

	/**
	 * An iterator that is safe to delete from 
	 * while spinning through the ACLs
	 */
	@Override
	public Iterator<io.fabric8.elasticsearch.plugin.acl.SearchGuardACL.Acl> iterator() {
		return new ArrayList<>(acls).iterator();
	}
	
	/**
	 * Remove an ACL
	 * @param acl
	 */
	public void remove(Acl acl){
		acls.remove(acl);
	}
	
	public void syncFrom(UserProjectCache cache, final String userProfilePrefix){
		removeSyncAcls();
		for (Map.Entry<String, Set<String>> userProjects : cache.getUserProjects().entrySet()) {
			AclBuilder builder = new AclBuilder()
				.user(userProjects.getKey())
				.bypass("*")
				.projects(formatIndicies(userProjects.getKey(), userProjects.getValue(), userProfilePrefix));
			if(cache.isClusterAdmin(userProjects.getKey())){
				builder.project(".operations.*");
			}
			acls.add(builder.build());
		}
	}
	
	public void createInitialACLs(Settings settings) {
		
		if ( acls == null )
			acls = new ArrayList<Acl>();

		// Create the default to deny all
		Acl defaultAcl = new AclBuilder().comment("Default is to deny all").build();
		acls.add(defaultAcl);
		
		final String[] aclNames = settings.getAsArray(OPENSHIFT_CONFIG_ACL_NAMES);
		for ( String name : aclNames ) {
			
			String propertyPrefix = OPENSHIFT_CONFIG_ACL_BASE + name;
			
			//check for "bypass" and "execute"
			String[] bypass = settings.getAsArray(propertyPrefix + ".bypass");
			String[] execute = settings.getAsArray(propertyPrefix + ".execute");

			for ( String b : bypass ) {
				String bypassComment = settings.get(propertyPrefix + "." + b + ".comment");
				AclBuilder bypassAcl = new AclBuilder().user(name).bypass(b).comment(bypassComment);
				
				// check for a specified indices
				String[] indices = settings.getAsArray(propertyPrefix + "." + b + ".indices");
				for ( String index : indices )
					bypassAcl.project(index);
				
				acls.add(bypassAcl.build());
			}
			
			for ( String e : execute ) {
				String executeComment = settings.get(propertyPrefix + "." + e + ".comment");
				AclBuilder executeAcl = new AclBuilder().user(name).executes(e).comment(executeComment);
				
				//check for a specified index
				String[] indices = settings.getAsArray(propertyPrefix + "." + e + ".indices");
				for ( String index : indices )
					executeAcl.project(index);
				
				acls.add(executeAcl.build());
			}			
			
		}

	}
	
	private List<String> formatIndicies(String user, Set<String> projects, final String userProfilePrefix){
		ArrayList<String> indicies = new ArrayList<>(projects.size());
		indicies.add(String.format("%s.%s", userProfilePrefix, getUsernameHash(user)));
		for (String project : projects) {
			indicies.add(String.format("%s.*", project));
		}
		return indicies;
	}
	private void removeSyncAcls() {
		for (Acl acl : new ArrayList<>(acls)) {
			if(acl.getComment() != null && acl.getComment().startsWith(OPENSHIFT_SYNC)){
				remove(acl);
			}
		}
	}
}
