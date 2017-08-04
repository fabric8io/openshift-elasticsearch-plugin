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

import static io.fabric8.elasticsearch.plugin.TestUtils.assertJson;
import static io.fabric8.elasticsearch.plugin.TestUtils.buildMap;
import static io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.PROJECT_PREFIX;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.common.settings.Settings;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.Samples;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRolesMapping.RolesMapping;

public class SearchGuardRolesMappingACLTest {

    private UserProjectCache cache = new UserProjectCacheMapAdapter(Settings.EMPTY);

    @Test
    public void testGeneratingKibanaUniqueRoleWithOpsUsers() throws Exception {
        cache.update("user1", "user2token", new HashSet<String>(), true);
        cache.update("user3", "user3token", new HashSet<String>(), true);
        cache.update("user2", "user2token", new HashSet<String>(Arrays.asList("foo.bar")), false);
        
        SearchGuardRolesMapping roles = new SearchGuardRolesMapping();
        RolesMappingSyncStrategy sync = new ProjectRolesMappingSyncStrategy(roles);
        sync.syncFrom(cache);
        
        assertJson("", Samples.ROLESMAPPING_OPS_SHARED_KIBANA_INDEX_WITH_UNIQUE.getContent(), roles.toMap());
    }

    @Test
    public void testGeneratingKibanaUniqueRoleWithOpsUsersSyncdToUserRolesMappings() throws Exception {
        cache.update("user1", "user2token", new HashSet<String>(), true);
        cache.update("user3", "user3token", new HashSet<String>(), true);
        cache.update("user2.bar@email.com", "user2token", new HashSet<String>(Arrays.asList("foo.bar")), false);
        
        SearchGuardRolesMapping roles = new SearchGuardRolesMapping();
        RolesMappingSyncStrategy sync = new UserRolesMappingSyncStrategy(roles);
        sync.syncFrom(cache);
        
        assertJson("", Samples.USER_ROLESMAPPING_STRATEGY.getContent(), roles.toMap());
    }
    
    @Test
    public void testDeserialization() throws Exception {
        new SearchGuardRolesMapping().load(buildMap(new StringReader(Samples.ROLESMAPPING_ACL.getContent())));
    }

    @Test
    public void testRemove() throws Exception {
        SearchGuardRolesMapping mappings = new SearchGuardRolesMapping()
                .load(buildMap(new StringReader(Samples.ROLESMAPPING_ACL.getContent())));
        for (RolesMapping mapping : mappings) {
            mappings.removeRolesMapping(mapping);
        }
    }

    @Test
    public void testSyncFromCache() throws Exception {

        // cache
        Map<SimpleImmutableEntry<String, String>, Set<String>> map = new HashMap<>();
        SimpleImmutableEntry<String, String> sie = new SimpleImmutableEntry<>("mytestuser", "tokenA");
        map.put(sie, new HashSet<>(Arrays.asList("projectA", "projectB", "projectC")));
        sie = new SimpleImmutableEntry<>("mythirduser", "tokenB");
        map.put(sie, new HashSet<>(Arrays.asList("projectzz")));
        Set<String> projects = new HashSet<>(Arrays.asList("projectA", "projectB", "projectC", "projectzz"));
        UserProjectCache cache = mock(UserProjectCache.class);
        when(cache.getUserProjects()).thenReturn(map, null);
        when(cache.getAllProjects()).thenReturn(projects);

        SearchGuardRolesMapping mappings = new SearchGuardRolesMapping()
                .load(buildMap(new StringReader(Samples.ROLESMAPPING_ACL.getContent())));
        RolesMappingSyncStrategy sync = new ProjectRolesMappingSyncStrategy(mappings);
        sync.syncFrom(cache);


        // assert acl added
        assertAclsHas(mappings, createRolesMapping("mythirduser", "projectzz"));

        // assert acl updated
        assertAclsHas(mappings, createRolesMapping("mytestuser", "projectA", "projectB", "projectC"));

        // assert acl removed
        assertNoAclForUser(mappings, "myotheruser");
    }

    private void assertNoAclForUser(final SearchGuardRolesMapping mappings, final String user) {
        for (RolesMapping mapping : mappings) {
            assertFalse("Exp. to not find any rolesmapping for users not in the cache",
                    mapping.getUsers().contains(user));
        }
    }

    private List<RolesMapping> createRolesMapping(String user, String... projects) {

        List<RolesMapping> mappings = new ArrayList<RolesMapping>();

        for (String project : projects) {
            RolesMapping mapping = new RolesMapping();
            String projectRoleName = String.format("%s_%s", PROJECT_PREFIX, project.replace('.', '_'));
            mapping.setName(projectRoleName);
            mapping.setUsers(Arrays.asList(user));

            mappings.add(mapping);
        }

        return mappings;
    }

    private void assertAclsHas(SearchGuardRolesMapping mappings, List<RolesMapping> exp) {

        int expectedCount = exp.size();
        int actualCount = 0;
        int found;

        for (RolesMapping act : exp) {
            found = 0;
            for (RolesMapping mapping : mappings) {
                if (mapping.getName().equals(act.getName())) {
                    found++;
                    actualCount++;
                    // users
                    assertArrayEquals("roles.clusters", mapping.getUsers().toArray(), act.getUsers().toArray());
                }
            }
            assertEquals("Expected the acl to exist and only once for role ", 1, found);
        }
        assertEquals("Was able to match " + actualCount + " of " + expectedCount + " ACLs", expectedCount, actualCount);
    }
}
