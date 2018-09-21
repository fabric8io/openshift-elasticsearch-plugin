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

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.rest.RestStatus;

import com.jayway.jsonpath.JsonPath;

import io.fabric8.elasticsearch.plugin.model.Project;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OpenshiftAPIService {
    
    private static final String ACCEPT = "Accept";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final Logger LOGGER = Loggers.getLogger(OpenshiftAPIService.class);
    
    public String userName(final String token) {
        Response response = null;
        try (DefaultOpenShiftClient client = buildClient(token)) {
            Request okRequest = new Request.Builder()
                    .url(client.getMasterUrl() + "apis/user.openshift.io/v1/users/~")
                    .header(ACCEPT, APPLICATION_JSON)
                    .build();
            response = client.getHttpClient().newCall(okRequest).execute();
            final String body = response.body().string();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Response: code '{}' {}", response.code(), body);
            }
            if(response.code() != RestStatus.OK.getStatus()) {
                throw new ElasticsearchSecurityException("Unable to determine username from the token provided", RestStatus.fromCode(response.code()));
            }
            return JsonPath.read(body,"$.metadata.name");
        } catch (IOException e) {
            LOGGER.error("Error retrieving username from token", e);
            throw new ElasticsearchException(e);
        }        
    }
    
    public Set<Project> projectNames(final String token){
        try (DefaultOpenShiftClient client = buildClient(token)) {
            Request request = new Request.Builder()
                .url(client.getMasterUrl() + "apis/project.openshift.io/v1/projects")
                .header(ACCEPT, APPLICATION_JSON)
                .build();
            Response response = client.getHttpClient().newCall(request).execute();
            if(response.code() != RestStatus.OK.getStatus()) {
                throw new ElasticsearchSecurityException("Unable to retrieve users's project list", RestStatus.fromCode(response.code()));
            }
            Set<Project> projects = new HashSet<>();
            List<Map<String, String>> raw = JsonPath.read(response.body().byteStream(), "$.items[*].metadata");
            for (Map<String, String> map : raw) {
                projects.add(new Project(map.get("name"), map.get("uid")));
            }
            return projects;
        } catch (KubernetesClientException e) {
            LOGGER.error("Error retrieving project list", e);
            throw new ElasticsearchSecurityException(e.getMessage());
        } catch (IOException e) {
            LOGGER.error("Error retrieving project list", e);
            throw new ElasticsearchException(e);
        }
    }
    
    /**
     * Execute a LocalSubectAccessReview
     * 
     * @param token             a token to check
     * @param project           the namespace to check against
     * @param verb              the verb (e.g. view)
     * @param resource          the resource (e.g. pods/log)
     * @param resourceAPIGroup  the group of the resource being checked
     * @param scopes            the scopes:
     *                            null  - use token scopes
     *                            empty - remove scopes
     *                            list  - an array of scopes
     *                            
     * @return  true if the SAR is satisfied
     */
    public boolean localSubjectAccessReview(final String token, 
            final String project, final String verb, final String resource, final String resourceAPIGroup, final String [] scopes) {
        try (DefaultOpenShiftClient client = buildClient(token)) {
            XContentBuilder payload = XContentFactory.jsonBuilder()
                .startObject()
                    .field("kind","LocalSubjectAccessReview")
                    .field("apiVersion","authorization.openshift.io/v1")
                    .field("verb", verb)
                    .field("resourceAPIGroup", resourceAPIGroup)
                    .field("resource", resource)
                    .field("namespace", project)
                    .array("scopes", scopes)
                .endObject();
            Request request = new Request.Builder()
                    .url(String.format("%sapis/authorization.openshift.io/v1/namespaces/%s/localsubjectaccessreviews", client.getMasterUrl(), project))
                    .header(CONTENT_TYPE, APPLICATION_JSON)
                    .header(ACCEPT, APPLICATION_JSON)
                    .post(RequestBody.create(MediaType.parse(APPLICATION_JSON), payload.string()))
                    .build();
            Response response = client.getHttpClient().newCall(request).execute();
            if(response.code() != RestStatus.CREATED.getStatus()) {
                throw new ElasticsearchSecurityException("Unable to determine user's operations role", RestStatus.fromCode(response.code()));
            }
            return JsonPath.read(response.body().byteStream(), "$.allowed");
        } catch (IOException e) {
            LOGGER.error("Error determining user's role", e);
        }
        return false;
    }
    
    private DefaultOpenShiftClient buildClient(final String token) {
        Config config = new ConfigBuilder().withOauthToken(token).build();
        return new DefaultOpenShiftClient(config);
    }
}
