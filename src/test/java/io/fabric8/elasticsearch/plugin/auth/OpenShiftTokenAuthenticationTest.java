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

package io.fabric8.elasticsearch.plugin.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.floragunn.searchguard.user.AuthCredentials;
import com.floragunn.searchguard.user.User;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.KibanaIndexMode;
import io.fabric8.elasticsearch.plugin.OpenshiftAPIService;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginServiceFactory;
import io.fabric8.elasticsearch.plugin.acl.BaseRolesSyncStrategy;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRolesMapping;
import io.fabric8.elasticsearch.util.TestRestRequest;

public class OpenShiftTokenAuthenticationTest {

    private OpenShiftTokenAuthentication backend;
    private RestRequest request;
    private String username = "tokenUserName@test.com";
    private OpenshiftRequestContextFactory contextFactory = mock(OpenshiftRequestContextFactory.class);
    private OpenshiftRequestContext context = new OpenshiftRequestContext(username, "theAuthToken", true, new HashSet<>(), ".kibana", KibanaIndexMode.SHARED_OPS);
    private ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
    private OpenshiftAPIService apiService = mock(OpenshiftAPIService.class);
    
    @Before
    public void setUp() throws Exception {
        String content = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("subjectAccessReviews")
                        .startObject("prometheus")
                            .field("namespace", "theNamespace")
                            .field("verb", "theVerb")
                            .field("resourceAPIGroup", "theGroup")
                            .field("resource", "theResource")
                        .endObject()
                    .endObject()
                .endObject()
            .string();
        backend = new OpenShiftTokenAuthentication(Settings.builder().loadFromSource(content, XContentType.JSON).build());
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        headers.put("Authorization", Arrays.asList("Bearer theAuthToken"));
        request = new TestRestRequest(headers);
        
        PluginServiceFactory.setContextFactory(contextFactory);
        PluginServiceFactory.setThreadContext(threadContext);
        PluginServiceFactory.setApiService(apiService);
        PluginServiceFactory.markReady();
    }
    
    @After
    public void tearDown() {
        PluginServiceFactory.setContextFactory(null);
        PluginServiceFactory.setApiService(null);
        PluginServiceFactory.setThreadContext(null);
        PluginServiceFactory.markNotReady();
    }
    
    @Test
    public void testAuthenticate() throws Exception {
        when(apiService.localSubjectAccessReview(anyString(), 
                anyString(), anyString(), anyString(), anyString(), eq(ArrayUtils.EMPTY_STRING_ARRAY)))
            .thenReturn(true);
        when(contextFactory.create(any(RestRequest.class))).thenReturn(context);
        
        threadContext.putTransient(ConfigurationSettings.OPENSHIFT_REQUEST_CONTEXT, 
            new OpenshiftRequestContext(username, "atoken", false, Collections.emptySet(), null, null));
        
        User expUser = new User(username);
        expUser.addRole(BaseRolesSyncStrategy.formatUserRoleName(username));
        expUser.addRole(BaseRolesSyncStrategy.formatUserKibanaRoleName(username));
        expUser.addRole(SearchGuardRolesMapping.ADMIN_ROLE);
        expUser.addRole("prometheus");
        
        User user = backend.authenticate(new AuthCredentials(username));
        assertEquals(expUser, user);
    }
    
    @Test
    public void testAuthenticateApiServiceWhenOpenshiftRequestContextIsNull() {
        assertNull(backend.authenticate(new AuthCredentials(username)));
    }
    
    @Test
    public void testExtractCredentials() throws Exception {
        when(contextFactory.create(any(RestRequest.class))).thenReturn(context);
        when(apiService.localSubjectAccessReview(anyString(), 
                anyString(), anyString(), anyString(), anyString(), eq(ArrayUtils.EMPTY_STRING_ARRAY)))
            .thenReturn(true);

        AuthCredentials creds = backend.extractCredentials(request, threadContext);
        assertEquals(username, creds.getUsername());
        assertEquals(context, threadContext.getTransient(ConfigurationSettings.OPENSHIFT_REQUEST_CONTEXT));
        
        assertTrue(creds.isComplete());
    }

    @SuppressWarnings("unchecked")
    @Test(expected = ElasticsearchSecurityException.class)
    public void testExtractCredentialsThrowsSecurityException() throws Exception {
        when(contextFactory.create(any(RestRequest.class))).thenThrow(ElasticsearchSecurityException.class);
        PluginServiceFactory.setContextFactory(contextFactory);
        
        backend.extractCredentials(request, null);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testExtractCredentialsCatchesException() throws Exception {
        when(contextFactory.create(any(RestRequest.class))).thenThrow(RuntimeException.class);
        PluginServiceFactory.setContextFactory(contextFactory);
        
        assertNull(backend.extractCredentials(request, threadContext));
    }
    
    /*
     * The case where a request is coming in while possible the
     * server is still initializing and this plugin is not ready
     * to service requests
     */
    @Test
    public void testExtractCredentialsWhenNotInitialized() {
        assertNull(backend.extractCredentials(null, null));
    }
    
    @Test
    public void testExtractCredentialsForNonBearerToken() {
        assertNull(backend.extractCredentials(request, null));
    }
    
    @Test
    public void testExistsAnyUser() {
        assertTrue(backend.exists(new User("username")));
    }
    
    @Test
    public void testExistsNullUser() {
        assertFalse(backend.exists(null));
    }
    
    @Test
    public void testGetType() {
        assertEquals("io.fabric8.elasticsearch.plugin.auth.OpenShiftTokenAuthentication", backend.getType());
    }

    @Test
    public void testReRequestAuthenticationIsDisabled() {
        assertFalse(backend.reRequestAuthentication(null, null));
    }
}
