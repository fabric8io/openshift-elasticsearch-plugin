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

package io.fabric8.elasticsearch;

import org.junit.Test;

import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory;


public class ProxyUsernameIntegrationTest extends ElasticsearchIntegrationTest {

    @Test
    public void testClusterAdminUserWithSimpleNameCreateProfileIndex() throws Exception {
        givenUserIsClusterAdmin("admin");
        givenUserIsAdminForProjects("logging", "openshift");
        
        whenGettingDocument("_cat/indices");
        
        assertThatResponseIsSuccessful();
    }
    
    @Test
    public void testNormalUserWithSimpleNameCreateProfileIndex() throws Exception {
        givenUserIsNotClusterAdmin("somerandomuser");
        givenUserIsAdminForProjects("myproject");
        
        whenGettingDocument("_cat/indices");
        assertThatResponseIsForbidden();
    }
    
    @Test
    public void testUsersWithSimpleNameCreatesProfileIndex() throws Exception {
        String [] users = {"foo@email.com", 
            "CN=jdoe,OU=DL IT,OU=User Accounts,DC=example,DC=com", 
            "test\\username",
            "CN=Lastname\\\\, Firstname,OU=Users,OU=TDBFG,DC=d2-tdbfg,DC=com"
            };
        
        for (String username : users) {
            givenUserIsNotClusterAdmin(username);
            givenUserIsAdminForProjects("myproject");

            whenCheckingIndexExists(".kibana." + OpenshiftRequestContextFactory.getUsernameHash(formatUserName(username)));
            assertThatResponseIsSuccessful();
        }
    }

}
