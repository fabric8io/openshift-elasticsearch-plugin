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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.Samples;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices.Type;

public class SearchGuardRoleACLTest {


	@Before
	public void setup(){
		
	}

	@Test
	public void testDeserialization() throws Exception {
		new SearchGuardRoles().load(buildMap(new StringReader(Samples.ROLES_ACL.getContent())));
	}

	@Test
	public void testRemove() throws Exception {
		SearchGuardRoles roles = new SearchGuardRoles().load(buildMap(new StringReader(Samples.ROLES_ACL.getContent())));
		for (Roles role : roles) {
			roles.removeRole(role);
		}
	}
	
	@Test
	public void testSyncFromCache() throws Exception {
		SearchGuardRoles roles = new SearchGuardRoles().load(buildMap(new StringReader(Samples.ROLES_ACL.getContent())));

		//cache
		Map<String, Set<String>> map = new HashMap<>();
		map.put("mytestuser", new HashSet<>(Arrays.asList("projectA", "projectB", "projectC")));
		map.put("mythirduser", new HashSet<>(Arrays.asList("projectzz")));
		Set<String> projects = new HashSet<>(Arrays.asList("projectA", "projectB", "projectC", "projectzz"));
		UserProjectCache cache = mock(UserProjectCache.class);
		when(cache.getUserProjects()).thenReturn(map);
		when(cache.getAllProjects()).thenReturn(projects);
		
		roles.syncFrom(cache, ConfigurationSettings.DEFAULT_USER_PROFILE_PREFIX);
		
		//assert acl added
		assertAclsHas(roles, createRoles("projectzz"));
	
		//assert acl updated
		assertAclsHas(roles, createRoles("projectA", "projectB", "projectC"));
		
		//assert acl removed
		assertNoAclForProject(roles, "myotherproject");
	}
	
	private void assertNoAclForProject(final SearchGuardRoles roles, final String project) {
		for (Roles role : roles) {
			Indices index = new Indices();
			index.setIndex(project);
			index.setTypes(buildDefaultTypes());
			
			assertFalse("Exp. to not find any roles for projects not in the cache", role.getIndices().toString().contains(index.getIndex()));
		}
	}
	
	private List<Roles> createRoles(String... projects){
		RolesBuilder builder = new RolesBuilder();
		
		for (String project : projects ) {
			String projectName = String.format("%s_%s", "gen_project", project.replace('.', '_')); 
			String indexName = String.format("%s?*", project.replace('.', '?'));
			
			RoleBuilder role = new RoleBuilder(projectName)
					.setActions(indexName, "*", new String[]{"indices:data/read*", "indices:admin/mappings/fields/get*", "indices:admin/validate/query*", "indices:admin/get*"});
			
			builder.addRole(role.build());
		}
		
		return builder.build();
	}
	
	private void assertAclsHas(SearchGuardRoles roles, List<Roles> exp) {
		
		int expectedCount = exp.size(),
		    found = 0;
		
		for ( Roles act : exp ) {
			for ( Roles role : roles ) {
				
				if ( role.getName().equals(act.getName()) ) {
					found++;
					// check name
					assertEquals(role.getName(), act.getName());
					
					// check clusters
					assertArrayEquals("roles.clusters", role.getCluster().toArray(), act.getCluster().toArray());
					
					// check indices
					assertEquals("roles.indices", role.getIndices().toString(), act.getIndices().toString());
				}
			}
		}
		assertEquals("Was able to match " + found + " of " + expectedCount + " ACLs", expectedCount, found);
	}
	
	private List<Type> buildDefaultTypes() {
		Type[] types = {new Type()};
		types[0].setType("*");
		types[0].setActions(Arrays.asList(new String[]{"ALL"}));
		
		return Arrays.asList(types);
	}
	
	private Map<String, Object> buildMap(StringReader reader) {
		return (Map<String, Object>) new Yaml().load(reader);
	}
}
