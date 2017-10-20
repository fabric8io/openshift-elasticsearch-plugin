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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.common.settings.Settings;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.KibanaIndexMode;
import io.fabric8.elasticsearch.plugin.Samples;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles.Indices.Type;

public class SearchGuardRoleACLTest {

    private UserProjectCache cache = new UserProjectCacheMapAdapter(Settings.EMPTY);

    @Test
    public void testGeneratingKibanaUniqueRoleWithOpsUsers() throws Exception {
        cache.update("user1", "user2token", new HashSet<String>(), true);
        cache.update("user3", "user3token", new HashSet<String>(), true);
        cache.update("user2", "user2token", new HashSet<String>(Arrays.asList("foo.bar")), false);
        
        SearchGuardRoles roles = new SearchGuardRoles();
        ProjectRolesSyncStrategy strat = new ProjectRolesSyncStrategy(roles, ".kibana", ".project", KibanaIndexMode.SHARED_OPS);
        strat.syncFrom(cache);
        
        assertJson("", Samples.ROLES_OPS_SHARED_KIBANA_INDEX_WITH_UNIQUE.getContent(), roles.toMap());
    }
    
    @Test
    public void testGeneratingKibanaUniqueRoleWithOpsUsersSyncedToUserRoles() throws Exception {
        cache.update("user1", "user2token", new HashSet<String>(Arrays.asList("user2-proj")), true);
        cache.update("user3", "user3token", new HashSet<String>(Arrays.asList("user3-proj")), true);
        cache.update("user2.bar@email.com", "user2token", new HashSet<String>(Arrays.asList("foo.bar", "xyz")), false);
        
        SearchGuardRoles roles = new SearchGuardRoles();
        RolesSyncStrategy strat = new UserRolesSyncStrategy(roles, ".kibana", ".project", KibanaIndexMode.SHARED_OPS);
        strat.syncFrom(cache);
        
        assertJson("", Samples.USER_ROLES_STRATEGY.getContent(), roles.toMap());
    }
    
    @Test
    public void testGeneratingKibanaOpsRole() throws Exception {
        UserProjectCache cache = new UserProjectCacheMapAdapter(Settings.EMPTY);
        cache.update("user1", "user2token", new HashSet<String>(), true);
        cache.update("user2", "user2token", new HashSet<String>(), true);
        
        SearchGuardRoles roles = new SearchGuardRoles();
        ProjectRolesSyncStrategy strat = new ProjectRolesSyncStrategy(roles, ".kibana", ".project", KibanaIndexMode.SHARED_OPS);
        strat.syncFrom(cache);
        
        assertJson("", Samples.ROLES_OPS_SHARED_KIBANA_INDEX.getContent(), roles.toMap());
    }

    @Test
    public void testGeneratingKibanaOpsShared() throws Exception {
        UserProjectCache cache = new UserProjectCacheMapAdapter(Settings.EMPTY);
        cache.update("user1", "user2token", new HashSet<String>(), true);
        cache.update("user2", "user2token", new HashSet<String>(), true);
        
        SearchGuardRoles roles = new SearchGuardRoles();
        ProjectRolesSyncStrategy strat = new ProjectRolesSyncStrategy(roles, ".kibana", ".project", KibanaIndexMode.SHARED_OPS);
        strat.syncFrom(cache);
        
        assertJson("", Samples.ROLES_SHARED_OPS_KIBANA_INDEX.getContent(), roles.toMap());
    }
    
    @Test
    public void testGeneratingKibanaNonOpsShared() throws Exception {
        UserProjectCache cache = new UserProjectCacheMapAdapter(Settings.EMPTY);
        cache.update("user1", "user2token", new HashSet<String>(), false);
        cache.update("user2", "user2token", new HashSet<String>(), false);
        
        SearchGuardRoles roles = new SearchGuardRoles();
        ProjectRolesSyncStrategy strat = new ProjectRolesSyncStrategy(roles, ".kibana", ".project", KibanaIndexMode.SHARED_NON_OPS);
        strat.syncFrom(cache);
        
        assertJson("", Samples.ROLES_SHARED_NON_OPS_KIBANA_INDEX.getContent(), roles.toMap());
    }
    
    @Test
    public void testGeneratingKibanaShared() throws Exception {
        UserProjectCache cache = new UserProjectCacheMapAdapter(Settings.EMPTY);
        cache.update("user1", "user2token", new HashSet<String>(), true);
        cache.update("user2", "user2token", new HashSet<String>(), false);
        
        SearchGuardRoles roles = new SearchGuardRoles();
        ProjectRolesSyncStrategy strat = new ProjectRolesSyncStrategy(roles, ".kibana", ".project", KibanaIndexMode.SHARED_NON_OPS);
        strat.syncFrom(cache);
        
        assertJson("", Samples.ROLES_SHARED_KIBANA_INDEX.getContent(), roles.toMap());
    }
    
    @Test
    public void testDeserialization() throws Exception {
        new SearchGuardRoles().load(buildMap(new StringReader(Samples.ROLES_ACL.getContent())));
    }

    @Test
    public void testRemove() throws Exception {
        SearchGuardRoles roles = new SearchGuardRoles()
                .load(buildMap(new StringReader(Samples.ROLES_ACL.getContent())));
        for (Roles role : roles) {
            roles.removeRole(role);
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
        when(cache.getUserProjects()).thenReturn(map);
        when(cache.getAllProjects()).thenReturn(projects);

        SearchGuardRoles roles = new SearchGuardRoles()
                .load(buildMap(new StringReader(Samples.ROLES_ACL.getContent())));
        ProjectRolesSyncStrategy strat = new ProjectRolesSyncStrategy(roles, 
                ConfigurationSettings.DEFAULT_USER_PROFILE_PREFIX, 
                ConfigurationSettings.OPENSHIFT_DEFAULT_PROJECT_INDEX_PREFIX, 
                KibanaIndexMode.SHARED_OPS);
        strat.syncFrom(cache);

        // assert acl added
        assertAclsHas(roles, createRoles("projectzz"));

        // assert acl updated
        assertAclsHas(roles, createRoles("projectA", "projectB", "projectC"));

        // assert acl removed
        assertNoAclForProject(roles, "myotherproject");
    }

    private void assertNoAclForProject(final SearchGuardRoles roles, final String project) {
        for (Roles role : roles) {
            Indices index = new Indices();
            index.setIndex(project);
            index.setTypes(buildDefaultTypes());

            assertFalse("Exp. to not find any roles for projects not in the cache",
                    role.getIndices().toString().contains(index.getIndex()));
        }
    }

    private List<Roles> createRoles(String... projects) {
        RolesBuilder builder = new RolesBuilder();

        for (String project : projects) {
            String projectName = String.format("%s_%s", "gen_project", project.replace('.', '_'));
            String indexName = String.format("%s?*", project.replace('.', '?'));

            RoleBuilder role = new RoleBuilder(projectName).setActions(indexName, "*",
                new String[] { "INDEX_PROJECT" });

            builder.addRole(role.build());
        }

        return builder.build();
    }

    private void assertAclsHas(SearchGuardRoles roles, List<Roles> expected) {

        int expectedCount = expected.size();
        int found = 0;

        for (Roles exp : expected) {
            for (Roles role : roles) {

                if (role.getName().equals(exp.getName())) {
                    found++;
                    // check name
                    assertEquals(role.getName(), exp.getName());

                    // check clusters
                    assertArrayEquals("roles.clusters", exp.getCluster().toArray(), role.getCluster().toArray());

                    // check indices
                    assertEquals("roles.indices", exp.getIndices().toString(), role.getIndices().toString());
                }
            }
        }
        assertEquals("Was able to match " + found + " of " + expectedCount + " ACLs", expectedCount, found);
    }

    private List<Type> buildDefaultTypes() {
        Type[] types = { new Type() };
        types[0].setType("*");
        types[0].setActions(Arrays.asList(new String[] { "ALL" }));

        return Arrays.asList(types);
    }
}
