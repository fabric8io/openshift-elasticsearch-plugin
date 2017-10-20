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

import static io.fabric8.elasticsearch.plugin.KibanaIndexMode.SHARED_NON_OPS;
import static io.fabric8.elasticsearch.plugin.KibanaIndexMode.SHARED_OPS;
import static io.fabric8.elasticsearch.plugin.KibanaIndexMode.UNIQUE;
import static io.fabric8.elasticsearch.plugin.KibanaUserReindexFilter.getUsernameHash;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestRequest;

import io.fabric8.elasticsearch.plugin.acl.UserProjectCache;
import io.fabric8.elasticsearch.util.RequestUtils;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 * Context of information regarding a use
 */
public class OpenshiftRequestContextFactory  {

    private static final ESLogger LOGGER = Loggers.getLogger(OpenshiftRequestContextFactory.class);
    
    private final OpenshiftClientFactory clientFactory;
    private final RequestUtils utils;
    private final String[] operationsProjects;
    private final String kibanaPrefix;
    private String kibanaIndexMode;

    @Inject
    public OpenshiftRequestContextFactory(final Settings settings, final RequestUtils utils, final OpenshiftClientFactory clientFactory) {
        this.clientFactory = clientFactory;
        this.utils = utils;
        this.operationsProjects = settings.getAsArray(ConfigurationSettings.OPENSHIFT_CONFIG_OPS_PROJECTS,
                ConfigurationSettings.DEFAULT_OPENSHIFT_OPS_PROJECTS);
        this.kibanaPrefix = settings.get(ConfigurationSettings.KIBANA_CONFIG_INDEX_NAME, ConfigurationSettings.DEFAULT_USER_PROFILE_PREFIX);
        this.kibanaIndexMode = settings.get(ConfigurationSettings.OPENSHIFT_KIBANA_INDEX_MODE, UNIQUE);
        if(!ArrayUtils.contains(new String [] {UNIQUE, SHARED_OPS, SHARED_NON_OPS}, kibanaIndexMode.toLowerCase())) {
            this.kibanaIndexMode = UNIQUE;
        }
        LOGGER.info("Using kibanaIndexMode: '{}'", this.kibanaIndexMode);
    }

    /**
     * Create a user context from the given request
     * 
     * @param   cache - The cache of user projects to create ACLs
     * @return  an OpenshiftRequestContext 
     * @throws  All exceptions
     */
    public OpenshiftRequestContext create(final RestRequest request, final UserProjectCache cache) throws Exception {
        logRequest(request, cache);

        Set<String> projects = new HashSet<>();
        boolean isClusterAdmin = false;
        String user = utils.getUser(request);
        if(user.contains("\\")){
            user = user.replaceAll("\\\\", "/");
            utils.setUser(request, user);
        }
        String token = utils.getBearerToken(request);
        if (StringUtils.isNotBlank(user) && StringUtils.isNotBlank(token)){
            isClusterAdmin = utils.isOperationsUser(request);
                
            if(!cache.hasUser(user, token)) {
                projects = listProjectsFor(user, token);
            }
        }
        return new OpenshiftRequestContext(user, token, isClusterAdmin, projects, getKibanaIndex(user, isClusterAdmin), this.kibanaIndexMode);
    }
    
    private void logRequest(final RestRequest request, final UserProjectCache cache) {
        if (LOGGER.isDebugEnabled()) {
            String user = utils.getUser(request);
            String token = utils.getBearerToken(request);
            LOGGER.debug("Handling Request... {}", request.uri());
            if(LOGGER.isTraceEnabled()) {
                List<String> headers = new ArrayList<>();
                for (Entry<String, String> entry : request.headers()) {
                    if(RequestUtils.AUTHORIZATION_HEADER.equals(entry.getKey())){
                        headers.add(entry.getKey() + "=Bearer <REDACTED>");
                    }else {
                        headers.add(entry.getKey() + "=" + entry.getValue());
                    }
                }
                LOGGER.trace("Request headers: {}", request.headers());
                LOGGER.trace("Request context: {}", request.getContext());
            }
            LOGGER.debug("Evaluating request for user '{}' with a {} token", user,
                    (StringUtils.isNotEmpty(token) ? "non-empty" : "empty"));
            LOGGER.debug("Cache has user: {}", cache.hasUser(user, token));
        }
    }

    // WARNING: This function must perform authentication with the given token.
    // This
    // is the only authentication performed in this plugin. This function must
    // throw
    // an exception if the token is invalid.
    private Set<String> listProjectsFor(final String user, final String token) throws Exception {
        Set<String> names = new HashSet<>();
        try {
            ConfigBuilder builder = new ConfigBuilder().withOauthToken(token);
            try (OpenShiftClient client = clientFactory.create(builder.build())) {
                List<Project> projects = client.projects().list().getItems();
                for (Project project : projects) {
                    if (!isBlacklistProject(project.getMetadata().getName())) {
                        names.add(project.getMetadata().getName() + "." + project.getMetadata().getUid());
                    }
                }
            }
        } catch (KubernetesClientException e) {
            LOGGER.error("Error retrieving project list for '{}'", e, user);
            throw new ElasticsearchSecurityException(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error retrieving project list for '{}'", e, user);
        }
        return names;
    }
    
    private boolean isBlacklistProject(String project) {
        return ArrayUtils.contains(operationsProjects, project.toLowerCase());
    }
    
    private String getKibanaIndex(String username, boolean isOpsUser) {
        return getKibanaIndex(kibanaPrefix, kibanaIndexMode, username, isOpsUser);
    }
    
    public static String getKibanaIndex(final String kibanaPrefix, final String kibanaIndexMode, final String username, final boolean isOpsUser) {
        if(StringUtils.isBlank(username)) {
            return "";
        }
        if((SHARED_OPS.equals(kibanaIndexMode) || SHARED_NON_OPS.equals(kibanaIndexMode)) && isOpsUser) {
            return kibanaPrefix;
        }
        if(SHARED_NON_OPS.equals(kibanaIndexMode)) {
            return kibanaPrefix + "_non_ops";
        }
        return kibanaPrefix + "." + getUsernameHash(username);
        
    }
    
    public static class OpenshiftRequestContext {
        
        public static final OpenshiftRequestContext EMPTY = new OpenshiftRequestContext("","",false, new HashSet<String>(), "", UNIQUE);

        private final String user;
        private final String token;
        private final boolean isClusterAdmin;
        private final Set<String> projects;
        private final String kibanaIndex;
        private final String kibanaIndexMode;

        protected OpenshiftRequestContext(final String user, final String token, boolean isClusterAdmin, 
                Set<String> projects, String kibanaIndex, final String kibanaIndexMode) {
            this.user = user;
            this.token = token;
            this.isClusterAdmin = isClusterAdmin;
            this.projects = new HashSet<>(projects);
            this.kibanaIndex = kibanaIndex;
            this.kibanaIndexMode = kibanaIndexMode;
        }

        /**
         * Method to determine if context has a none empty username and token
         * 
         * @return true if there is a non-empty user and token
         */
        public boolean isAuthenticated() {
            return StringUtils.isNotEmpty(token) && StringUtils.isNotEmpty(user);
        }

        public String getUser() {
            return this.user;
        }

        public String getToken() {
            return this.token;
        }

        public boolean isOperationsUser() {
            return isClusterAdmin;
        }

        /**
         * The Set of projects with UUID
         * 
         * @return the set of project names formatted with their UUID (e.g. project.UUID)
         */
        public Set<String> getProjects() {
            return projects;
        }
        
        public String getKibanaIndex() {
            return this.kibanaIndex;
        }
        
        public String getKibanaIndexMode() {
            return this.kibanaIndexMode;
        }
    }
}
