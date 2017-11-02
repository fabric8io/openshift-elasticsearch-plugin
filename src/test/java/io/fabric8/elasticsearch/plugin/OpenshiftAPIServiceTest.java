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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.elasticsearch.common.settings.Settings;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import io.fabric8.elasticsearch.plugin.OpenshiftAPIService.OpenShiftClientFactory;
import io.fabric8.elasticsearch.plugin.model.Project;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.api.model.ProjectBuilder;
import io.fabric8.openshift.api.model.ProjectListBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.server.mock.OpenShiftServer;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

public class OpenshiftAPIServiceTest {

    @Rule
    public OpenShiftServer apiServer = new OpenShiftServer();
    private final PluginSettings pluginSettings = new PluginSettings(Settings.EMPTY);
    private OpenshiftAPIService service = new OpenshiftAPIService(pluginSettings);

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

    @Test
    public void testLocalSubjectAccessReviewWhenNotNonResourceURL() throws IOException{
        OkHttpClient okClient = mock(OkHttpClient.class);
        DefaultOpenShiftClient client = mock(DefaultOpenShiftClient.class);
        OpenShiftClientFactory factory = mock(OpenShiftClientFactory.class);
        Call call = mock(Call.class);
        when(factory.buildClient(eq(pluginSettings), anyString())).thenReturn(client);
        when(client.getHttpClient()).thenReturn(okClient);
        when(client.getMasterUrl()).thenReturn(new URL("https://localhost:8443/"));
        
        Response response = new Response.Builder()
            .request(new Request.Builder().url("https://localhost:8443").build())
            .code(201)
            .protocol(Protocol.HTTP_1_1)
            .message("")
            .body(ResponseBody.create(MediaType.parse("application/json;utf-8"), "{\"allowed\":true}"))
            .build();

        RequestAnswer answer = new RequestAnswer(call);
        when(okClient.newCall(any(Request.class))).thenAnswer(answer);
        when(call.execute()).thenReturn(response);
        
        service = new OpenshiftAPIService(factory, pluginSettings);
        
        assertTrue(service.localSubjectAccessReview("sometoken", "openshift-logging", "get", "pod/metrics", null, ArrayUtils.EMPTY_STRING_ARRAY));
        Buffer buffer = new Buffer();
        assertEquals("https://localhost:8443/apis/authorization.openshift.io/v1/subjectaccessreviews",answer.getRequest().url().toString());
        answer.getRequest().body().writeTo(buffer);
        String exp = "{\"kind\":\"SubjectAccessReview\","
                + "\"apiVersion\":\"authorization.openshift.io/v1\",\"verb\":\"get\",\"scopes\":[],\"resourceAPIGroup\":null,"
                + "\"resource\":\"pod/metrics\",\"namespace\":\"openshift-logging\"}";
        assertEquals(exp, new String(buffer.readByteArray()));
    }

    @Test
    public void testLocalSubjectAccessReviewForNonResourceURL() throws IOException{
        OkHttpClient okClient = mock(OkHttpClient.class);
        DefaultOpenShiftClient client = mock(DefaultOpenShiftClient.class);
        OpenShiftClientFactory factory = mock(OpenShiftClientFactory.class);
        Call call = mock(Call.class);
        when(factory.buildClient(eq(pluginSettings), anyString())).thenReturn(client);
        when(client.getHttpClient()).thenReturn(okClient);
        when(client.getMasterUrl()).thenReturn(new URL("https://localhost:8443/"));
        
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://localhost:8443").build())
                .code(201)
                .protocol(Protocol.HTTP_1_1)
                .message("")
                .body(ResponseBody.create(MediaType.parse("application/json;utf-8"), "{\"allowed\":true}"))
                .build();
        
        RequestAnswer answer = new RequestAnswer(call);
        when(okClient.newCall(any(Request.class))).thenAnswer(answer);
        when(call.execute()).thenReturn(response);
        
        service = new OpenshiftAPIService(factory, pluginSettings);
        
        assertTrue(service.localSubjectAccessReview("sometoken", "openshift-logging", "get", "/metrics", null, ArrayUtils.EMPTY_STRING_ARRAY));
        Buffer buffer = new Buffer();
        answer.getRequest().body().writeTo(buffer);
        assertEquals("https://localhost:8443/apis/authorization.openshift.io/v1/subjectaccessreviews",answer.getRequest().url().toString());
        String exp = "{\"kind\":\"SubjectAccessReview\","
                + "\"apiVersion\":\"authorization.openshift.io/v1\",\"verb\":\"get\",\"scopes\":[],"
                + "\"isNonResourceURL\":true,\"path\":\"/metrics\"}";
        assertEquals(exp, new String(buffer.readByteArray()));
    }
    
    class RequestAnswer implements Answer<Call> {
        
        private Request request;
        private Call call;

        RequestAnswer(Call call){
            this.call = call;
        }
        
        public Request getRequest() {
            return this.request;
        }

        @Override
        public Call answer(InvocationOnMock invocation) throws Throwable {
            request = (Request) invocation.getArguments()[0];
            return call;
        }
    }
    
}
