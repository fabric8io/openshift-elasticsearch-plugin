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

import org.junit.Before;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.KibanaIndexMode;
import okhttp3.Headers;

/*
 * This integration test verifies the ability (or not) to access Elasticsearch
 * depending upon what credentials are provided
 */
public class AccessControlIntegrationTest extends ElasticsearchIntegrationTest {


    @Before
    public void setup() throws Exception {
        givenDocumentIsIndexed("project.multi-tenancy-1.uuid.1970.01.01", "test", "0", "multi-tenancy-1-doc0");
        givenDocumentIsIndexed("project.multi-tenancy-2.uuid.1970.01.01", "test", "0", "multi-tenancy-2-doc0");
        givenDocumentIsIndexed("project.multi-tenancy-3.uuid.1970.01.01", "test", "0", "multi-tenancy-3-doc0");
    }

    @Test
    public void testUserAccessDeletedAndRecreatedNamespace() throws Exception {
        final String userName = "nonadminuser";
        //artificially seed user's kibanaIndex
        String kibanaIndex = getKibanaIndex(KibanaIndexMode.UNIQUE, userName, false);
        givenDocumentIsIndexed(kibanaIndex, "config", "0", "myKibanaIndex");
        givenDocumentIsIndexed("project.test.uuid1.1970.01.01", "test", "0", "test.uuid1-doc0");
        givenDocumentIsIndexed("project.test.uuid2.1970.01.01", "test", "0", "test.uuid2-doc0");

        givenUserIsNotClusterAdmin(userName);
        givenUserIsAdminForProject("test","uuid2");

        whenGettingDocument(String.format("%s/_count", formatProjectIndexPattern("test", "*")));
        assertThatResponseIsForbidden();

        whenGettingDocument(String.format("%s/_count", formatProjectIndexPattern("test", "uuid2")));
        assertThatResponseIsSuccessful();

    }

    @Test
    public void testUsersAccessWithTokenFollowedByWithout() throws Exception {
        final String [] projects = {"multi-tenancy-1", "multi-tenancy-2"};
        final String userName = "nonadminuser";
        //artificially seed user's kibanaIndex
        String kibanaIndex = getKibanaIndex(KibanaIndexMode.UNIQUE, userName, false);
        givenDocumentIsIndexed(kibanaIndex, "config", "0", "myKibanaIndex");
        givenUserIsNotClusterAdmin(userName);
        givenUserIsAdminForProjects(projects);
        
        final String uri = String.format("%s/_count", formatProjectIndexPattern("multi-tenancy-1","uuid"));
        
        //no username or token
        whenGettingDocument(uri);
        assertThatResponseIsSuccessful();
        
        Headers.Builder builder = new Headers.Builder()
            .add("connection","close")
            .add("x-proxy-remote-user", userName)
            .add("x-forwarded-for", "127.0.0.1");
        
        //username with no token
        whenGettingDocument(uri, builder.build());
        assertThatResponseIsUnauthorized();

    }
    
    @Test
    public void testUsersAccessWithNoToken() throws Exception {
        final String [] projects = {"multi-tenancy-1", "multi-tenancy-2"};
        final String userName = "nonadminuser";
        //artificially seed user's kibanaIndex
        String kibanaIndex = getKibanaIndex(KibanaIndexMode.UNIQUE, userName, false);
        givenDocumentIsIndexed(kibanaIndex, "config", "0", "myKibanaIndex");
        givenUserIsNotClusterAdmin(userName);
        givenUserIsAdminForProjects(projects);
        
        final String uri = String.format("%s/_count", formatProjectIndexPattern("multi-tenancy-1","uuid"));
        
        Headers.Builder builder = new Headers.Builder()
                .add("connection","close")
                .add("x-forwarded-for", "127.0.0.1");
        
        //no username or token
        whenGettingDocument(uri, builder.build());
        assertThatResponseIsUnauthorized();
        
        //username with no token
        builder.add("x-proxy-remote-user", userName);
        whenGettingDocument(uri, builder.build());
        assertThatResponseIsUnauthorized();
        
    }
    
    @Test
    public void testUsersAccessWithInvalidToken() throws Exception {
        final String uri = String.format("%s/_count", formatProjectIndexPattern("multi-tenancy-1","uuid"));
        final String userName = "nonadminuser";
        
        Headers.Builder builder = new Headers.Builder()
                .add("connection","close")
                .add("X-Forwarded-For", "127.0.0.1");
        
        givenUserHasBadToken();

        //no username with empty token
        builder.add("Authorization", "");
        whenGettingDocument(uri, builder.build());
        assertThatResponseIsUnauthorized();
        
        //username with empty token
        builder.add("X-Proxy-Remote-User", userName);
        whenGettingDocument(uri, builder.build());
        assertThatResponseIsUnauthorized();

        //username with invalid token
        builder.add("Authorization", "Bearer bogusToken");
        whenGettingDocument(uri, builder.build());
        assertThatResponseIsUnauthorized();

    }

}
