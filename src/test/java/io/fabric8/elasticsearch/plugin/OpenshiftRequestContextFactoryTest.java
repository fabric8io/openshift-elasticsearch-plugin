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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestRequest;
import org.junit.Before;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.KibanaUserReindexFilter;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.acl.UserProjectCache;
import io.fabric8.elasticsearch.util.RequestUtils;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.dsl.ClientNonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ClientResource;
import io.fabric8.openshift.api.model.DoneableProject;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectBuilder;
import io.fabric8.openshift.api.model.ProjectList;
import io.fabric8.openshift.api.model.ProjectListBuilder;
import io.fabric8.openshift.client.OpenShiftClient;

public class OpenshiftRequestContextFactoryTest {

    private Settings.Builder settingsBuilder = Settings.builder();
    private OpenshiftRequestContextFactory factory;
    private OpenshiftRequestContext context;
    private OpenshiftClientFactory clientFactory = mock(OpenshiftClientFactory.class);
    private RestRequest request;
    private UserProjectCache cache = mock(UserProjectCache.class);
    private RequestUtils utils;

    @Before
    public void setUp() throws Exception {
        request = mock(RestRequest.class);
        when(request.header(eq(ConfigurationSettings.DEFAULT_AUTH_PROXY_HEADER))).thenReturn("fooUser");
        when(request.header(eq("Authorization"))).thenReturn("Bearer ABC123");
        givenUserIsCashed(true);
    }

    private void givenUserIsCashed(boolean cached) {
        when(cache.hasUser(anyString(), anyString())).thenReturn(cached);
    }

    private void givenUserContextFactory(boolean isOperationsUser) {
        Settings settings = settingsBuilder.build();
        utils = spy(new RequestUtils(settings));
        doReturn(isOperationsUser).when(utils).isOperationsUser(any(RestRequest.class));

        factory = new OpenshiftRequestContextFactory(settings, utils, clientFactory);
    }

    @SuppressWarnings("unchecked")
    private void givenUserHasProjects() {
        OpenShiftClient client = mock(OpenShiftClient.class);
        ClientNonNamespaceOperation<Project, ProjectList, DoneableProject, ClientResource<Project, DoneableProject>> projects = mock(
                ClientNonNamespaceOperation.class);
        ProjectList projectList = new ProjectListBuilder(false)
                .addToItems(new ProjectBuilder(false).withNewMetadata().withName("foo").endMetadata().build()).build();
        when(projects.list()).thenReturn(projectList);
        when(client.projects()).thenReturn(projects);
        when(clientFactory.create(any(Config.class))).thenReturn(client);
    }

    private void givenKibanaIndexMode(String value) {
        settingsBuilder.put(ConfigurationSettings.OPENSHIFT_KIBANA_INDEX_MODE, value);
    }

    private OpenshiftRequestContext whenCreatingUserContext() throws Exception {
        this.context = factory.create(request, cache);
        return this.context;
    }

    private void assertKibanaIndexIs(String mode) {
        switch (mode) {
        case "shared_ops":
            assertEquals("Exp. Kibana index mode to to be '.kibana'", ConfigurationSettings.DEFAULT_USER_PROFILE_PREFIX,
                    context.getKibanaIndex());
            break;
        case "shared_non_ops":
            assertEquals("Exp. Kibana index mode to to be '.kibana_non_ops'",
                    ConfigurationSettings.DEFAULT_USER_PROFILE_PREFIX + "_non_ops", context.getKibanaIndex());
            break;
        case "unique":
            assertEquals("Exp. Kibana index mode to to be '.kibana.<userhash>'",
                    ConfigurationSettings.DEFAULT_USER_PROFILE_PREFIX + "."
                            + KibanaUserReindexFilter.getUsernameHash(context.getUser()),
                    context.getKibanaIndex());
            break;
        default:
            fail("Unable to assert the kibana index since the kibanaIndexMode is unrecognized: " + mode);
        }
    }

    @Test
    public void testCreatingUserContextWhenUserHasBackSlash() throws Exception {
        when(request.header(eq(ConfigurationSettings.DEFAULT_AUTH_PROXY_HEADER))).thenReturn("test\\user");

        givenUserContextFactory(false);
        whenCreatingUserContext();
        assertEquals("test/user", context.getUser());
    }

    @Test
    public void testGetKibanaIndexWhenUnrecognizedSharedMode() throws Exception {
        givenKibanaIndexMode("some random value");
        givenUserContextFactory(false);
        whenCreatingUserContext();
        assertKibanaIndexIs("unique");
    }

    @Test
    public void testGetKibanaIndexWhenDefaultSharedMode() throws Exception {
        givenUserContextFactory(false);
        whenCreatingUserContext();
        assertKibanaIndexIs("unique");
    }

    @Test
    public void testGetKibanaIndexWhenUniqueMode() throws Exception {
        givenKibanaIndexMode("unique");
        givenUserContextFactory(false);
        whenCreatingUserContext();
        assertKibanaIndexIs("unique");
    }

    @Test
    public void testGetKibanaIndexWhenOpsSharedModeForOperationsUser() throws Exception {
        givenKibanaIndexMode("shared_ops");
        givenUserContextFactory(true);
        whenCreatingUserContext();
        assertKibanaIndexIs("shared_ops");
    }

    @Test
    public void testGetKibanaIndexWhenNonOpsSharedModeForOperationsUser() throws Exception {
        givenKibanaIndexMode("shared_non_ops");
        givenUserContextFactory(true);
        whenCreatingUserContext();
        assertKibanaIndexIs("shared_ops");
    }

    @Test
    public void testGetKibanaIndexWhenNonOpsSharedModeForNonOperationsUser() throws Exception {
        givenKibanaIndexMode("shared_non_ops");
        givenUserContextFactory(false);
        whenCreatingUserContext();
        assertKibanaIndexIs("shared_non_ops");
    }

    @Test
    public void testGetKibanaIndexWhenOpsSharedModeForNonOperationsUser() throws Exception {
        givenKibanaIndexMode("shared_ops");
        givenUserContextFactory(false);
        whenCreatingUserContext();
        assertKibanaIndexIs("unique");
    }

    @Test
    public void testCreateUserContextWhenRequestHasUsernameAndPassword() throws Exception {
        givenUserContextFactory(true);
        givenUserHasProjects();
        givenUserIsCashed(false);
        whenCreatingUserContext();
        assertTrue("Exp. the request context to have a users projects", !context.getProjects().isEmpty());
        assertTrue("Exp. the request context to identify an ops user", context.isOperationsUser());
    }

}
