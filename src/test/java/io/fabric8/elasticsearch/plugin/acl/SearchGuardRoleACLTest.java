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
import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.KibanaIndexMode;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.Samples;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles;

public class SearchGuardRoleACLTest {

    private SearchGuardRoles roles = new SearchGuardRoles();
    
    private OpenshiftRequestContext givenContextFor(String user, boolean isOperations, String mode, String...projects) {
        return new OpenshiftRequestContext(user, "", isOperations, new HashSet<>(Arrays.asList(projects)), "abc", mode);
    }
    
    private ProjectRolesSyncStrategy givenProjectRolesSyncStrategyFor(String mode) {
        return new ProjectRolesSyncStrategy(roles, ".kibana", ".project", mode, 10);
    }

    private UserRolesSyncStrategy givenUserRolesSyncStrategyFor(String mode) {
        return new UserRolesSyncStrategy(roles, ".kibana", ".project", mode, 15);
    }
    
    @Test
    public void testGeneratingKibanaUniqueRoleWithOpsUsers() throws Exception {
        ProjectRolesSyncStrategy strat = givenProjectRolesSyncStrategyFor(KibanaIndexMode.SHARED_OPS);
        strat.syncFrom(givenContextFor("user1", true, KibanaIndexMode.SHARED_OPS));
        strat.syncFrom(givenContextFor("user2", false, KibanaIndexMode.SHARED_OPS, "foo.bar"));
        
        assertJson("", Samples.ROLES_OPS_SHARED_KIBANA_INDEX_WITH_UNIQUE.getContent(), roles.toMap());
    }

    
    @Test
    public void testGeneratingKibanaUniqueRoleWithOpsUsersSyncedToUserRoles() throws Exception {
        RolesSyncStrategy strat = givenUserRolesSyncStrategyFor(KibanaIndexMode.UNIQUE);
        
        strat.syncFrom(givenContextFor("user1", true, KibanaIndexMode.UNIQUE, "user2-proj"));
        strat.syncFrom(givenContextFor("user3", true, KibanaIndexMode.UNIQUE, "user3-proj"));
        strat.syncFrom(givenContextFor("user2.bar@email.com", false, KibanaIndexMode.UNIQUE, "xyz", "foo.bar"));
        strat.syncFrom(givenContextFor("CN=jdoe,OU=DL IT,OU=User Accounts,DC=example,DC=com", false, KibanaIndexMode.UNIQUE, "distinguishedproj"));
        
        assertJson("", Samples.USER_ROLES_STRATEGY.getContent(), roles.toMap());
    }
    
    @Test
    public void testGeneratingKibanaOpsRole() throws Exception {
        ProjectRolesSyncStrategy strat = givenProjectRolesSyncStrategyFor(KibanaIndexMode.SHARED_OPS);
        strat.syncFrom(givenContextFor("user1", true, KibanaIndexMode.SHARED_OPS));
        strat.syncFrom(givenContextFor("user2", true, KibanaIndexMode.SHARED_OPS));
        
        assertJson("", Samples.ROLES_OPS_SHARED_KIBANA_INDEX.getContent(), roles.toMap());
    }

    @Test
    public void testGeneratingKibanaOpsShared() throws Exception {
        ProjectRolesSyncStrategy strat = givenProjectRolesSyncStrategyFor(KibanaIndexMode.SHARED_OPS);
        strat.syncFrom(givenContextFor("user1", true, KibanaIndexMode.SHARED_OPS));
        strat.syncFrom(givenContextFor("user2", true, KibanaIndexMode.SHARED_OPS));
        
        assertJson("", Samples.ROLES_SHARED_OPS_KIBANA_INDEX.getContent(), roles.toMap());
    }
    
    @Test
    public void testGeneratingKibanaNonOpsShared() throws Exception {
        ProjectRolesSyncStrategy strat = givenProjectRolesSyncStrategyFor(KibanaIndexMode.SHARED_NON_OPS);
        strat.syncFrom(givenContextFor("user1", false, KibanaIndexMode.SHARED_NON_OPS));
        strat.syncFrom(givenContextFor("user2", false, KibanaIndexMode.SHARED_NON_OPS));
        
        assertJson("", Samples.ROLES_SHARED_NON_OPS_KIBANA_INDEX.getContent(), roles.toMap());
    }
    
    @Test
    public void testGeneratingKibanaShared() throws Exception {
        ProjectRolesSyncStrategy strat = givenProjectRolesSyncStrategyFor(KibanaIndexMode.SHARED_NON_OPS);
        strat.syncFrom(givenContextFor("user1", true, KibanaIndexMode.SHARED_NON_OPS));
        strat.syncFrom(givenContextFor("user2", false, KibanaIndexMode.SHARED_NON_OPS));
        
        assertJson("", Samples.ROLES_SHARED_KIBANA_INDEX.getContent(), roles.toMap());
    }
    
    @Test
    public void testSerialization() throws Exception {
        Roles role = new RolesBuilder().newRoleBuilder("foo")
            .addClusterAction("myClusterAction")
            .addIndexAction("*", "*", "*")
            .expires(12345L)
            .build();
        SearchGuardRoles sgRoles = new SearchGuardRoles(8901L);
        sgRoles.addAll(Arrays.asList(role));
        final String out = XContentHelper.convertToJson(sgRoles.toXContentBuilder().bytes(), true, true);
        Map<String, Object> in = XContentHelper.convertToMap(new BytesArray(out), true).v2();
        SearchGuardRoles inRoles = new SearchGuardRoles().load(in);
        assertEquals("Exp serialization to equal derialization", out, XContentHelper.convertToJson(inRoles.toXContentBuilder().bytes(), true, true));
    }

    @Test
    public void testRemove() throws Exception {
        SearchGuardRoles roles = new SearchGuardRoles()
                .load(buildMap(new StringReader(Samples.ROLES_ACL.getContent())));
        for (Roles role : roles) {
            roles.removeRole(role);
        }
    }

}
