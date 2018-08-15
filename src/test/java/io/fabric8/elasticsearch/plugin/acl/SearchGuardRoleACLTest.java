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

import static io.fabric8.elasticsearch.plugin.TestUtils.assertYaml;
import static io.fabric8.elasticsearch.plugin.TestUtils.buildMap;
import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.KibanaIndexMode;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.Samples;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles;

public class SearchGuardRoleACLTest {

    private SearchGuardRoles roles = new SearchGuardRoles();
    
    private OpenshiftRequestContext givenContextFor(String user, boolean isOperations, String mode, String...projects) {
        return new OpenshiftRequestContext(user, "", isOperations, new HashSet<>(Arrays.asList(projects)), "someKibanaIndexValue", mode);
    }
    
    private ProjectRolesSyncStrategy givenProjectRolesSyncStrategyFor(String mode) {
        return new ProjectRolesSyncStrategy(roles, ".kibana", ".project", mode, 10);
    }

    private UserRolesSyncStrategy givenUserRolesSyncStrategyFor(String mode) {
        return new UserRolesSyncStrategy(roles, ".kibana", ".project", mode, 15);
    }
    
    @Test
    public void testProjectStrategyForRolesWithUniqueKibanaIndexMode() throws Exception {
        ProjectRolesSyncStrategy strat = givenProjectRolesSyncStrategyFor(KibanaIndexMode.UNIQUE);
        strat.syncFrom(givenContextFor("user1", true, KibanaIndexMode.UNIQUE, "foo.bar"));
        strat.syncFrom(givenContextFor("user2", false, KibanaIndexMode.UNIQUE, "foo.bar"));
        
        assertYaml("", Samples.PROJECT_STRATEGY_ROLES_UNIQUE_KIBANA_MODE.getContent(), roles);
    }

    @Test
    public void testProjectStrategyForRolesWithSharedOpsKibanaIndexMode() throws Exception {
        ProjectRolesSyncStrategy strat = givenProjectRolesSyncStrategyFor(KibanaIndexMode.SHARED_OPS);
        strat.syncFrom(givenContextFor("user1", true, KibanaIndexMode.SHARED_OPS, "foo.bar"));
        strat.syncFrom(givenContextFor("user2", false, KibanaIndexMode.SHARED_OPS, "foo.bar"));
        
        assertYaml("", Samples.PROJECT_STRATEGY_ROLES_SHARED_OPS_KIBANA_MODE.getContent(), roles);
    }
    
    @Test
    public void testProjectStrategyForRolesWithSharedNonOpsKibanaIndexMode() throws Exception {
        ProjectRolesSyncStrategy strat = givenProjectRolesSyncStrategyFor(KibanaIndexMode.SHARED_NON_OPS);
        strat.syncFrom(givenContextFor("user1", false, KibanaIndexMode.SHARED_NON_OPS, "foo.bar"));
        strat.syncFrom(givenContextFor("user2", true, KibanaIndexMode.SHARED_NON_OPS, "foo.bar"));
        
        assertYaml("", Samples.PROJECT_STRATEGY_ROLES_SHARED_NON_OPS_KIBANA_MODE.getContent(), roles);
    }

    @Test
    public void testUserStrategyForRolesWithUniqueKibanaIndexMode() throws Exception {
        RolesSyncStrategy strat = givenUserRolesSyncStrategyFor(KibanaIndexMode.UNIQUE);
        
        strat.syncFrom(givenContextFor("user1", true, KibanaIndexMode.UNIQUE, "user2-proj"));
        strat.syncFrom(givenContextFor("user3", true, KibanaIndexMode.UNIQUE, "user3-proj"));
        strat.syncFrom(givenContextFor("user2.bar@email.com", false, KibanaIndexMode.UNIQUE, "xyz", "foo.bar"));
        strat.syncFrom(givenContextFor("CN=jdoe,OU=DL IT,OU=User Accounts,DC=example,DC=com", false, KibanaIndexMode.UNIQUE, "distinguishedproj"));
        
        assertYaml("", Samples.USER_STRATEGY_ROLES_UNIQUE_KIBANA_MODE.getContent(), roles);
    }

    @Test
    public void testUserStrategyForRolesWithSharedOpsKibanaIndexMode() throws Exception {
        RolesSyncStrategy strat = givenUserRolesSyncStrategyFor(KibanaIndexMode.SHARED_OPS);
        
        strat.syncFrom(givenContextFor("user1", true, KibanaIndexMode.SHARED_OPS, "user2-proj"));
        strat.syncFrom(givenContextFor("user3", true, KibanaIndexMode.SHARED_OPS, "user3-proj"));
        strat.syncFrom(givenContextFor("user2.bar@email.com", false, KibanaIndexMode.SHARED_OPS, "xyz", "foo.bar"));
        strat.syncFrom(givenContextFor("CN=jdoe,OU=DL IT,OU=User Accounts,DC=example,DC=com", false, KibanaIndexMode.SHARED_OPS, "distinguishedproj"));
        
        assertYaml("", Samples.USER_STRATEGY_ROLES_SHARED_OPS_KIBANA_MODE.getContent(), roles);
    }

    @Test
    public void testUserStrategyForRolesWithSharedNonOpsKibanaIndexMode() throws Exception {
        RolesSyncStrategy strat = givenUserRolesSyncStrategyFor(KibanaIndexMode.SHARED_NON_OPS);
        
        strat.syncFrom(givenContextFor("user1", true, KibanaIndexMode.SHARED_NON_OPS, "user2-proj"));
        strat.syncFrom(givenContextFor("user3", true, KibanaIndexMode.SHARED_NON_OPS, "user3-proj"));
        strat.syncFrom(givenContextFor("user2.bar@email.com", false, KibanaIndexMode.SHARED_NON_OPS, "xyz", "foo.bar"));
        strat.syncFrom(givenContextFor("CN=jdoe,OU=DL IT,OU=User Accounts,DC=example,DC=com", false, KibanaIndexMode.SHARED_NON_OPS, "distinguishedproj"));
        
        assertYaml("", Samples.USER_STRATEGY_ROLES_SHARED_NON_OPS_KIBANA_MODE.getContent(), roles);
    }
    

    
    @Test
    public void testSerialization() throws Exception {
        Roles role = new RolesBuilder().newRoleBuilder("foo")
            .addClusterAction("myClusterAction")
            .addIndexAction("*", "*", "*")
            .expires("12345")
            .build();
        SearchGuardRoles sgRoles = new SearchGuardRoles(8901L);
        sgRoles.addAll(Arrays.asList(role));
        final String out = XContentHelper.toString(sgRoles);
        Map<String, Object> in = XContentHelper.convertToMap(new BytesArray(out), true, XContentType.JSON).v2();
        SearchGuardRoles inRoles = new SearchGuardRoles().load(in);
        assertEquals("Exp serialization to equal derialization", out,  XContentHelper.toString(inRoles));
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
