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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestFilter;
import org.elasticsearch.rest.RestFilterChain;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;

import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.support.ConfigConstants;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.kibana.KibanaSeed;
import io.fabric8.elasticsearch.util.RequestUtils;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.SubjectAccessReviewResponse;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.NamespacedOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 * REST filter to update the ACL when a user first makes a request
 */
public class DynamicACLFilter extends RestFilter implements ConfigurationSettings {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final ESLogger LOGGER = Loggers.getLogger(DynamicACLFilter.class);

    private final UserProjectCache cache;
    private final String searchGuardIndex;
    private final String kibanaIndex;
    private final String kibanaVersion;
    private final String userProfilePrefix;
    private final ReentrantLock lock = new ReentrantLock();

    private final String kbnVersionHeader;
    private final String[] operationsProjects;

    private Boolean enabled;

    private final String cdmProjectPrefix;

    private KibanaSeed kibanaSeed;

    private final Client client;
    private final RequestUtils util;

    @Inject
    public DynamicACLFilter(final UserProjectCache cache, final Settings settings, final KibanaSeed seed, final Client client, final RequestUtils util) {
        this.client = client;
        this.cache = cache;
        this.kibanaSeed = seed;
        this.util = util;
        this.searchGuardIndex = settings.get(SEARCHGUARD_CONFIG_INDEX_NAME, DEFAULT_SECURITY_CONFIG_INDEX);
        this.userProfilePrefix = settings.get(OPENSHIFT_ES_USER_PROFILE_PREFIX, DEFAULT_USER_PROFILE_PREFIX);
        this.kibanaIndex = settings.get(KIBANA_CONFIG_INDEX_NAME, DEFAULT_USER_PROFILE_PREFIX);
        this.kibanaVersion = settings.get(KIBANA_CONFIG_VERSION, DEFAULT_KIBANA_VERSION);
        this.kbnVersionHeader = settings.get(KIBANA_VERSION_HEADER, DEFAULT_KIBANA_VERSION_HEADER);

        this.operationsProjects = settings.getAsArray(OPENSHIFT_CONFIG_OPS_PROJECTS, DEFAULT_OPENSHIFT_OPS_PROJECTS);
        this.cdmProjectPrefix = settings.get(OPENSHIFT_CONFIG_PROJECT_INDEX_PREFIX,
                OPENSHIFT_DEFAULT_PROJECT_INDEX_PREFIX);

        LOGGER.debug("searchGuardIndex: {}", this.searchGuardIndex);

        this.enabled = settings.getAsBoolean(OPENSHIFT_DYNAMIC_ENABLED_FLAG, OPENSHIFT_DYNAMIC_ENABLED_DEFAULT);

    }

    @Override
    public void process(RestRequest request, RestChannel channel, RestFilterChain chain) throws Exception {
        boolean continueProcessing = true;

        try {
            if (enabled) {
                // grab the kibana version here out of "kbn-version" if we can
                // -- otherwise use the config one
                String kbnVersion = getKibanaVersion(request);
                if (StringUtils.isEmpty(kbnVersion)) {
                    kbnVersion = kibanaVersion;
                }

                final String user = util.getUser(request);
                final String token = getBearerToken(request);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Handling Request... {}", request.uri());
                    if(LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Request headers: {}", request.headers());
                        LOGGER.trace("Request context: {}", request.getContext());
                    }
                    LOGGER.debug("Evaluating request for user '{}' with a {} token", user,
                            (StringUtils.isNotEmpty(token) ? "non-empty" : "empty"));
                    LOGGER.debug("Cache has user: {}", cache.hasUser(user, token));
                }
                if (StringUtils.isNotEmpty(token) && StringUtils.isNotEmpty(user) && !cache.hasUser(user, token)) {
                    final boolean isOperationsUser = isOperationsUser(user, token);
                    if (isOperationsUser) {
                        request.putInContext(OPENSHIFT_ROLES, "cluster-admin");
                    }
                    if (updateCache(user, token, isOperationsUser, kbnVersion)) {
                        syncAcl();
                    }

                }
            }
        } catch (ElasticsearchSecurityException ese) {
            LOGGER.info("Could not authenticate user");
            channel.sendResponse(new BytesRestResponse(RestStatus.UNAUTHORIZED));
            continueProcessing = false;
        } catch (Exception e) {
            LOGGER.error("Error handling request in {}", e, this.getClass().getSimpleName());
        } finally {
            if (continueProcessing) {
                chain.continueProcessing(request, channel);
            }
        }
    }

    private String getBearerToken(RestRequest request) {
        final String[] auth = ((String) ObjectUtils.defaultIfNull(request.header(AUTHORIZATION_HEADER), "")).split(" ");
        if (auth.length >= 2 && "Bearer".equals(auth[0])) {
            return auth[1];
        }
        return "";
    }

    private String getKibanaVersion(RestRequest request) {
        return (String) ObjectUtils.defaultIfNull(request.header(kbnVersionHeader), "");
    }

    private boolean updateCache(final String user, final String token, final boolean isOperationsUser,
            final String kbnVersion) {
        LOGGER.debug("Updating the cache for user '{}'", user);
        try {
            // This is the key to authentication. Before listProjectsFor, we
            // haven't actually authenticated
            // anything in this plugin, using the given token. If the token is
            // valid, we will get back a
            // list of projects for the token. If not, listProjectsFor will
            // throw an exception and we will
            // not update the cache with the user and token. In this way we will
            // keep bogus entries out of
            // the cache.
            Set<String> projects = listProjectsFor(token);
            cache.update(user, token, projects, isOperationsUser);

            Set<String> roles = new HashSet<String>();
            if (isOperationsUser) {
                roles.add("operations-user");
            }

            kibanaSeed.setDashboards(user, projects, roles, client, kibanaIndex, kbnVersion, cdmProjectPrefix);
        } catch (KubernetesClientException e) {
            LOGGER.error("Error retrieving project list for '{}'", e, user);
            throw new ElasticsearchSecurityException(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error retrieving project list for '{}'", e, user);
            return false;
        }
        return true;
    }

    // WARNING: This function must perform authentication with the given token.
    // This
    // is the only authentication performed in this plugin. This function must
    // throw
    // an exception if the token is invalid.
    private Set<String> listProjectsFor(final String token) throws Exception {
        ConfigBuilder builder = new ConfigBuilder().withOauthToken(token);
        Set<String> names = new HashSet<>();
        try (OpenShiftClient client = new DefaultOpenShiftClient(builder.build())) {
            List<Project> projects = client.projects().list().getItems();
            for (Project project : projects) {
                if (!isBlacklistProject(project.getMetadata().getName())) {
                    names.add(project.getMetadata().getName() + "." + project.getMetadata().getUid());
                }
            }
        }
        return names;
    }

    private boolean isBlacklistProject(String project) {
        return ArrayUtils.contains(operationsProjects, project.toLowerCase());
    }

    private boolean isOperationsUser(final String user, final String token) {
        ConfigBuilder builder = new ConfigBuilder().withOauthToken(token);
        boolean allowed = false;
        try (NamespacedOpenShiftClient osClient = new DefaultOpenShiftClient(builder.build())) {
            LOGGER.debug("Submitting a SAR to see if '{}' is able to retrieve logs across the cluster", user);
            SubjectAccessReviewResponse response = osClient.inAnyNamespace().subjectAccessReviews().createNew()
                    .withVerb("get").withResource("pods/log").done();
            allowed = response.getAllowed();
        } catch (Exception e) {
            LOGGER.error("Exception determining user's '{}' role.", e, user);
        } finally {
            LOGGER.debug("User '{}' isOperationsUser: {}", user, allowed);
        }
        return allowed;
    }

    private void syncAcl() {
        LOGGER.debug("Syncing the ACL to ElasticSearch");
        try {
            lock.lock();
            LOGGER.debug("Loading SearchGuard ACL...");

            final MultiGetRequest mget = new MultiGetRequest();
            mget.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true"); //header needed here
            mget.refresh(true);
            mget.realtime(true);
            mget.add(searchGuardIndex, SEARCHGUARD_ROLE_TYPE, SEARCHGUARD_CONFIG_ID);
            mget.add(searchGuardIndex, SEARCHGUARD_MAPPING_TYPE, SEARCHGUARD_CONFIG_ID);

            SearchGuardRoles roles = null;
            SearchGuardRolesMapping rolesMapping = null;
            MultiGetResponse response = client.multiGet(mget).actionGet();
            for (MultiGetItemResponse item : response.getResponses()) {
                if(!item.isFailed()) {
                    if(LOGGER.isDebugEnabled()){
                        LOGGER.debug("Read in {}: {}", item.getType(), XContentHelper.convertToJson(item.getResponse().getSourceAsBytesRef(), true, true));
                    }
                    switch (item.getType()) {
                    case SEARCHGUARD_ROLE_TYPE:
                        roles = new SearchGuardRoles().load(item.getResponse().getSource());
                        break;
                    case SEARCHGUARD_MAPPING_TYPE:
                        rolesMapping = new SearchGuardRolesMapping().load(item.getResponse().getSource());
                        break;
                    }
                }else {
                    LOGGER.error("There was a failure loading document type {}", item.getFailure(), item.getType());
                }
            }

            if(roles == null || rolesMapping == null) {
                return;
            }

            LOGGER.debug("Syncing from cache to ACL...");
            roles.syncFrom(cache, userProfilePrefix, cdmProjectPrefix);
            rolesMapping.syncFrom(cache, userProfilePrefix);

            writeAcl(roles, rolesMapping);
        } catch (Exception e) {
            LOGGER.error("Exception while syncing ACL with cache", e);
        } finally {
            lock.unlock();
        }
    }

    private void writeAcl(SearchGuardACLDocument... documents) throws Exception {

        BulkRequestBuilder builder = this.client.prepareBulk().setRefresh(true);

        for (SearchGuardACLDocument doc : documents) {
            UpdateRequest update = this.client
                    .prepareUpdate(searchGuardIndex, doc.getType(), SEARCHGUARD_CONFIG_ID)
                    .setConsistencyLevel(WriteConsistencyLevel.DEFAULT)
                    .setDoc(doc.toXContentBuilder())
                    .request();
            builder.add(update);
            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug("Built {} update request: {}", doc.getType(), XContentHelper.convertToJson(doc.toXContentBuilder().bytes(),true, true));
            }
        }
        BulkRequest request = builder.request();
        request.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
        BulkResponse response = this.client.bulk(request).actionGet();

        if(!response.hasFailures()) {
            ConfigUpdateRequest confRequest = new ConfigUpdateRequest(SEARCHGUARD_INITIAL_CONFIGS);
            confRequest.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
            ConfigUpdateResponse cur = this.client
                    .execute(ConfigUpdateAction.INSTANCE, confRequest).actionGet();
            if (cur.getNodes().length > 0) {
                LOGGER.debug("Successfully reloaded config with '{}' nodes", cur.getNodes().length);
            }else {
                LOGGER.warn("Failed to reloaded configs", cur.getNodes().length);
            }
        }else {
            LOGGER.error("Unable to write ACL {}", response.buildFailureMessage());
        }
    }

    @Override
    public int order() {
        // need to run before search guard
        return Integer.MIN_VALUE;
    }
}
