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
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.rest.RestRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.auth.BackendRoleRetriever;
import io.fabric8.elasticsearch.plugin.model.Project;
import io.fabric8.elasticsearch.util.RequestUtils;
import io.fabric8.elasticsearch.util.TestRestRequest;

@RunWith(value = Parameterized.class)
public class OpenshiftRequestContextFactoryTest {

    @Parameter(value = 0)
    public String authHeader;
    @Parameter(value = 1)
    public String authToken;

    private Settings.Builder settingsBuilder = Settings.builder();
    private OpenshiftRequestContextFactory factory;
    private OpenshiftRequestContext context;
    private OpenshiftAPIService apiService = mock(OpenshiftAPIService.class);
    private RestRequest request;
    private RequestUtils utils;

    @Before
    public void setUp() throws Exception {
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        headers.put(ConfigurationSettings.DEFAULT_AUTH_PROXY_HEADER, Arrays.asList("fooUser"));
        headers.put(authHeader, Arrays.asList(authToken));
        request = new TestRestRequest(headers);
        PluginServiceFactory.setBackendRoleRetriever(new BackendRoleRetriever() {
            
            @Override
            public Collection<String> retrieveBackendRoles(String token) {
                return Collections.emptyList();
            }
        });
        PluginServiceFactory.markReady();
    }
    
    @After
    public void teardown() {
        PluginServiceFactory.markNotReady();
    }

    @Parameters()
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
          {"authorization", "Bearer ABC123"},
          {"x-forwarded-access-token", "ABC123"}
        });
    }

    private void givenUserContextFactory(boolean isOperationsUser) {
        Settings settings = settingsBuilder.build();
        utils = spy(new RequestUtils(new PluginSettings(settings), apiService));
        doReturn(isOperationsUser).when(utils).isOperationsUser(anyString(), anyString());

        factory = new OpenshiftRequestContextFactory(settings, utils, apiService, new ThreadContext(settings));
    }

    private void givenUserHasProjects() {
        Set<Project> projects = new HashSet<>();
        projects.add(new Project("foo","foo"));
        when(apiService.projectNames(anyString())).thenReturn(projects);
    }

    private void givenKibanaIndexMode(String value) {
        settingsBuilder.put(ConfigurationSettings.OPENSHIFT_KIBANA_INDEX_MODE, value);
    }

    private OpenshiftRequestContext whenCreatingUserContext() throws Exception {
        return whenCreatingUserContext("someusername");
    }

    private OpenshiftRequestContext whenCreatingUserContext(String username) throws Exception {
        doReturn(username).when(utils).assertUser(anyString());
        this.context = factory.create(request);
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
                            + OpenshiftRequestContextFactory.getUsernameHash(context.getUser()),
                    context.getKibanaIndex());
            break;
        default:
            fail("Unable to assert the kibana index since the kibanaIndexMode is unrecognized: " + mode);
        }
    }

    @Test
    public void testCreatingUserContextWhenUserHasBackSlash() throws Exception {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(ConfigurationSettings.DEFAULT_AUTH_PROXY_HEADER, Arrays.asList("test\\user"));
        headers.put("authorization", Arrays.asList("Bearer ABC123"));
        request = new TestRestRequest(headers);
        givenUserContextFactory(false);
        givenUserHasProjects();
        whenCreatingUserContext("test\\user");
        assertEquals("test/user", context.getUser());
    }

    @Test
    public void testGetKibanaIndexWhenUnrecognizedSharedMode() throws Exception {
        givenKibanaIndexMode("some random value");
        givenUserContextFactory(false);
        givenUserHasProjects();
        whenCreatingUserContext();
        assertKibanaIndexIs("unique");
    }

    @Test
    public void testGetKibanaIndexWhenDefaultSharedMode() throws Exception {
        givenUserContextFactory(false);
        givenUserHasProjects();
        whenCreatingUserContext();
        assertKibanaIndexIs("unique");
    }

    @Test
    public void testGetKibanaIndexWhenUniqueMode() throws Exception {
        givenKibanaIndexMode("unique");
        givenUserContextFactory(false);
        givenUserHasProjects();
        whenCreatingUserContext();
        assertKibanaIndexIs("unique");
    }

    @Test
    public void testGetKibanaIndexWhenOpsSharedModeForOperationsUser() throws Exception {
        givenKibanaIndexMode("shared_ops");
        givenUserContextFactory(true);
        givenUserHasProjects();
        whenCreatingUserContext();
        assertKibanaIndexIs("shared_ops");
    }

    @Test
    public void testGetKibanaIndexWhenNonOpsSharedModeForOperationsUser() throws Exception {
        givenKibanaIndexMode("shared_non_ops");
        givenUserContextFactory(true);
        givenUserHasProjects();
        whenCreatingUserContext();
        assertKibanaIndexIs("shared_ops");
    }

    @Test
    public void testGetKibanaIndexWhenNonOpsSharedModeForNonOperationsUser() throws Exception {
        givenKibanaIndexMode("shared_non_ops");
        givenUserContextFactory(false);
        givenUserHasProjects();
        whenCreatingUserContext();
        assertKibanaIndexIs("shared_non_ops");
    }

    @Test
    public void testGetKibanaIndexWhenOpsSharedModeForNonOperationsUser() throws Exception {
        givenKibanaIndexMode("shared_ops");
        givenUserContextFactory(false);
        givenUserHasProjects();
        whenCreatingUserContext();
        assertKibanaIndexIs("unique");
    }

    @Test
    public void testCreateUserContextWhenRequestHasUsernameAndPassword() throws Exception {
        givenUserContextFactory(true);
        givenUserHasProjects();
        whenCreatingUserContext();
        assertTrue("Exp. the request context to not have users projects", context.getProjects().isEmpty());
        assertTrue("Exp. the request context to identify an ops user", context.isOperationsUser());
    }

}
