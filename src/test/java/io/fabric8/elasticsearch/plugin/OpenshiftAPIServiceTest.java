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

package io.fabric8.elasticsearch.plugin;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.model.Project;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.api.model.ProjectBuilder;
import io.fabric8.openshift.api.model.ProjectListBuilder;
import io.fabric8.openshift.client.server.mock.OpenShiftServer;

public class OpenshiftAPIServiceTest {

    @Rule
    public OpenShiftServer apiServer = new OpenShiftServer();
    private OpenshiftAPIService service = new OpenshiftAPIService();

    @Before
    public void setup() {
        final String basedir = System.getProperty("project.basedir");
        final String password = "changeit";
        final String keyStore = basedir + "/src/it/resources/keystore.jks";
        final String masterUrl = apiServer.getMockServer().url("/").toString();

        System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, masterUrl);
        System.setProperty("kubernetes.trust.certificates", "true");
        System.setProperty("kubernetes.keystore.file", keyStore);
        System.setProperty("kubernetes.keystore.passphrase", password);
        System.setProperty("kubernetes.truststore.file", keyStore);
        System.setProperty("kubernetes.truststore.passphrase", password);
    }
    
    private void givenProjects(String... projects) throws Exception {

        ProjectListBuilder builder = new ProjectListBuilder(false);
        for (String project : projects) {
            builder.addToItems(new ProjectBuilder(false)
                    .withNewMetadata()
                        .withUid(project)
                        .withName(project)
                    .endMetadata()
                .build());
        }
        apiServer.expect()
            .withPath("/apis/project.openshift.io/v1/projects")
            .andReturn(200, builder.build())
            .always();
    }

    @Test
    public void testProjectNames() throws Exception {
        givenProjects("foo", "bar");
        Set<Project> projects = service.projectNames("someToken");
        Set<Project> exp = new HashSet<Project>();
        exp.add(new Project("foo","foo"));
        exp.add(new Project("bar","bar"));
        assertEquals(exp, projects);
    }


}
