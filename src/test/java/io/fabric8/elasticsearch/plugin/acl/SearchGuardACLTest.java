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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.Samples;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardACL.Acl;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SearchGuardACLTest {
	
	private ObjectMapper mapper;

	@Before
	public void setup(){
		mapper = new ObjectMapper();
	}

	@Test
	public void testDeserialization() throws Exception {
		mapper.readValue(new StringReader(Samples.ACL.getContent()), SearchGuardACL.class);
	}

	@Test
	public void testRemove() throws Exception {
		SearchGuardACL acls = mapper.readValue(new StringReader(Samples.ACL.getContent()), SearchGuardACL.class);
		for (Acl acl : acls) {
			acls.remove(acl);
		}
	}
	
	@Test
	public void testSyncFromCache() throws Exception {
		SearchGuardACL acls = mapper.readValue(new StringReader(Samples.OPENSHIFT_ACL.getContent()), SearchGuardACL.class);

		//cache
		Map<String, Set<String>> map = new HashMap<>();
		map.put("mytestuser", new HashSet<>(Arrays.asList("projectA", "projectB", "projectC")));
		map.put("mythirduser", new HashSet<>(Arrays.asList("projectzz")));
		UserProjectCache cache = mock(UserProjectCache.class);
		when(cache.getUserProjects()).thenReturn(map);
		
		
		acls.syncFrom(cache, ConfigurationSettings.DEFAULT_USER_PROFILE_PREFIX);
		
		//assert acl added
		assertAclsHas(acls, createAcl("mythirduser","projectzz"));
	
		//assert acl updated
		assertAclsHas(acls, createAcl("mytestuser","projectA", "projectB", "projectC"));
		
		//assert acl removed
		assertNoAclForUser(acls, "myotheruser");
	}

	private void assertNoAclForUser(final SearchGuardACL acls, final String user) {
		for (Acl acl : acls) {
			assertFalse("Exp. to not find any acls for users not in the cache", acl.getUsers().contains(user));
		}
	}
	
	private Acl createAcl(String user, String... projects){
		Acl acl = new Acl();
		acl.setUsers(Arrays.asList(user));
		List<String> indicies = new ArrayList<>();
		for (String project : projects) {
			indicies.add(project +".*");
		}
		indicies.add(".kibana."+getUsernameHash(user));
		acl.setIndices(indicies);
		acl.setFiltersBypass(Arrays.asList("*"));
		return acl;
	}
	
	private void assertAclsHas(SearchGuardACL acls, Acl exp) {
		int found = 0;
		for (Acl acl : acls) {
			if(acl.getUsers().containsAll(exp.getUsers())){
				found++;
				assertArrayEquals("acl.users", exp.getUsers().toArray(), acl.getUsers().toArray());
				assertArrayEquals("acl.filter_bypass", exp.getFiltersBypass().toArray(), acl.getFiltersBypass().toArray());
				assertArrayEquals("acl.filter_execute", exp.getFiltersExecute().toArray(), acl.getFiltersExecute().toArray());
				Object[] expIndexes = exp.getIndices().toArray();
				Object[] actualIndexes = acl.getIndices().toArray();
				
				Arrays.sort(expIndexes);
				Arrays.sort(actualIndexes);
				assertArrayEquals("acl.indices", expIndexes, actualIndexes);
			}
		}
		assertEquals("Expected the acl to exist and only once for user " + StringUtils.join(exp.getUsers(), ",") , 1, found);
	}
}
