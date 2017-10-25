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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestFilter;
import org.elasticsearch.rest.RestFilterChain;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.transport.ConnectTransportException;

import com.floragunn.searchguard.SearchGuardPlugin;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.ssl.SearchGuardSSLPlugin;
import com.floragunn.searchguard.ssl.util.SSLConfigConstants;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.kibana.KibanaSeed;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.SubjectAccessReviewResponse;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.NamespacedOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 * REST filter to update the ACL when a user first makes a request
 *
 * @author jeff.cantrill
 *
 */
public class DynamicACLFilter extends RestFilter implements ConfigurationSettings {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final ESLogger logger;
    private final UserProjectCache cache;
    private final String proxyUserHeader;
    private final TransportClient esClient;
    private final String searchGuardIndex;
    private final String kibanaIndex;
    private final String kibanaVersion;
    private final String userProfilePrefix;
    private final Settings settings;
    private final ReentrantLock lock = new ReentrantLock();

    private final String kbnVersionHeader;
    private final String[] operationsProjects;

    private Boolean enabled;

    private final String cdmProjectPrefix;

    private KibanaSeed kibanaSeed;

    @Inject
    public DynamicACLFilter(final UserProjectCache cache, final Settings settings, final Client client,
            final KibanaSeed seed) {
        this.cache = cache;
        this.kibanaSeed = seed;
        this.logger = Loggers.getLogger(getClass(), settings);
        this.proxyUserHeader = settings.get(SEARCHGUARD_AUTHENTICATION_PROXY_HEADER, DEFAULT_AUTH_PROXY_HEADER);
        this.searchGuardIndex = settings.get(SEARCHGUARD_CONFIG_INDEX_NAME, DEFAULT_SECURITY_CONFIG_INDEX);
        this.userProfilePrefix = settings.get(OPENSHIFT_ES_USER_PROFILE_PREFIX, DEFAULT_USER_PROFILE_PREFIX);
        this.kibanaIndex = settings.get(KIBANA_CONFIG_INDEX_NAME, DEFAULT_USER_PROFILE_PREFIX);
        this.kibanaVersion = settings.get(KIBANA_CONFIG_VERSION, DEFAULT_KIBANA_VERSION);
        this.kbnVersionHeader = settings.get(KIBANA_VERSION_HEADER, DEFAULT_KIBANA_VERSION_HEADER);

        this.operationsProjects = settings.getAsArray(OPENSHIFT_CONFIG_OPS_PROJECTS, DEFAULT_OPENSHIFT_OPS_PROJECTS);
        this.cdmProjectPrefix = settings.get(OPENSHIFT_CONFIG_PROJECT_INDEX_PREFIX,
                OPENSHIFT_DEFAULT_PROJECT_INDEX_PREFIX);

        logger.debug("searchGuardIndex: {}", this.searchGuardIndex);

        this.settings = settings;

        this.enabled = settings.getAsBoolean(OPENSHIFT_DYNAMIC_ENABLED_FLAG, OPENSHIFT_DYNAMIC_ENABLED_DEFAULT);
        // This is to not have SG print an error with us loading the SG_SSL
        // plugin for our transport client
        System.setProperty("sg.nowarn.client", "true");

        /** Build the esClient as a transport client **/
        String clusterName = settings.get("cluster.name");
        String keystore = settings.get(SG_CLIENT_KS_PATH, DEFAULT_SG_CLIENT_KS_PATH);
        String truststore = settings.get(SG_CLIENT_TS_PATH, DEFAULT_SG_CLIENT_TS_PATH);
        String kspass = settings.get(SG_CLIENT_KS_PASS, DEFAULT_SG_CLIENT_KS_PASS);
        String tspass = settings.get(SG_CLIENT_TS_PASS, DEFAULT_SG_CLIENT_TS_PASS);
        String kstype = settings.get(SG_CLIENT_KS_TYPE, DEFAULT_SG_CLIENT_KS_TYPE);
        String tstype = settings.get(SG_CLIENT_TS_TYPE, DEFAULT_SG_CLIENT_TS_TYPE);

        Settings.Builder settingsBuilder = Settings.builder().put("path.home", ".").put("path.conf", ".")
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_FILEPATH, keystore)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_FILEPATH, truststore)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_PASSWORD, kspass)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_PASSWORD, tspass)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENFORCE_HOSTNAME_VERIFICATION, false)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENFORCE_HOSTNAME_VERIFICATION_RESOLVE_HOST_NAME,
                        false)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENABLED, true)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_TYPE, kstype)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_TYPE, tstype)

                .put("cluster.name", clusterName).put("client.transport.ignore_cluster_name", false)
                .put("client.transport.sniff", false);

        Settings clientSettings = settingsBuilder.build();

        this.esClient = TransportClient.builder().settings(clientSettings).addPlugin(SearchGuardSSLPlugin.class)
                .addPlugin(SearchGuardPlugin.class) // needed for config update action only
                .build();
        try {
            this.esClient.addTransportAddress(new InetSocketTransportAddress(new InetSocketAddress("localhost", 9300)));
        } catch (ConnectTransportException e) {
            this.logger.warn("Cluster may still be initializing. Please be patient: {}", e.getMessage());
        }
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

                final String user = getUser(request);
                final String token = getBearerToken(request);
                if (logger.isDebugEnabled()) {
                    logger.debug("Handling Request... {}", request.uri());
                    logger.debug("Evaluating request for user '{}' with a {} token", user,
                            (StringUtils.isNotBlank(token) ? "non-empty" : "empty"));
                    logger.debug("Cache has user: {}", cache.hasUser(user, token));
                }
                if (StringUtils.isNotBlank(token) && StringUtils.isNotBlank(user)) {
                    // this is key to auth - this is what validates the given token,
                    // and verifies that the token corresponds to the given username
                    // do not alter this without good reason
                    assertUser(request);
                    if (!cache.hasUser(user, token)) {
                        final boolean isOperationsUser = isOperationsUser(user, token);
                        if (isOperationsUser) {
                            request.putInContext(OPENSHIFT_ROLES, "cluster-admin");
                        }
                        if (updateCache(user, token, isOperationsUser, kbnVersion)) {
                            syncAcl();
                        }
                    }
                } else if (isClientCertAuth(request) && StringUtils.isBlank(user) && StringUtils.isBlank(token)) {
                    return; // nothing more we can do here
                } else {
                    String message = "Incorrect authentication credentials were given - must provide client cert, or username/token, or all 3."
                            + "  It is not correct to provide username without token, or token without username.";
                    logger.debug(message);
                    throw new ElasticsearchSecurityException(message);
                }
            }
        } catch (ElasticsearchSecurityException ese) {
            logger.info("Could not authenticate user");
            channel.sendResponse(new BytesRestResponse(RestStatus.UNAUTHORIZED));
            continueProcessing = false;
        } catch (Exception e) {
            logger.error("Error handling request in {}", e, this.getClass().getSimpleName());
        } finally {
            if (continueProcessing) {
                chain.continueProcessing(request, channel);
            }
        }
    }

    private String getUser(RestRequest request) {
        return (String) ObjectUtils.defaultIfNull(request.header(proxyUserHeader), "");
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
        logger.debug("Updating the cache for user '{}'", user);
        try {
            Set<String> projects = listProjectsFor(token);
            cache.update(user, token, projects, isOperationsUser);

            Set<String> roles = new HashSet<String>();
            if (isOperationsUser) {
                roles.add("operations-user");
            }

            kibanaSeed.setDashboards(user, projects, roles, esClient, kibanaIndex, kbnVersion, cdmProjectPrefix,
                    settings);
        } catch (KubernetesClientException e) {
            logger.error("Error retrieving project list for '{}'", e, user);
            throw new ElasticsearchSecurityException(e.getMessage());
        } catch (Exception e) {
            logger.error("Error retrieving project list for '{}'", e, user);
            return false;
        }
        return true;
    }

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
            logger.debug("Submitting a SAR to see if '{}' is able to retrieve logs across the cluster", user);
            SubjectAccessReviewResponse response = osClient.inAnyNamespace().subjectAccessReviews().createNew()
                    .withVerb("get").withResource("pods/log").done();
            allowed = response.getAllowed();
        } catch (Exception e) {
            logger.error("Exception determining user's '{}' role.", e, user);
        } finally {
            logger.debug("User '{}' isOperationsUser: {}", user, allowed);
        }
        return allowed;
    }

    private void syncAcl() {
        logger.debug("Syncing the ACL to ElasticSearch");
        try {
            lock.lock();
            logger.debug("Loading SearchGuard ACL...");

            SearchGuardRoles roles = readRolesACL(esClient);
            SearchGuardRolesMapping rolesMapping = readRolesMappingACL(esClient);

            logger.debug("Syncing from cache to ACL...");
            roles.syncFrom(cache, userProfilePrefix, cdmProjectPrefix);
            rolesMapping.syncFrom(cache, userProfilePrefix);

            writeACL(esClient, roles, rolesMapping);
        } catch (Exception e) {
            logger.error("Exception while syncing ACL with cache", e);
        } finally {
            lock.unlock();
        }
    }

    private SearchGuardRoles readRolesACL(Client esClient) throws IOException {
        GetRequest getRequest = esClient.prepareGet(searchGuardIndex, SEARCHGUARD_ROLE_TYPE, SEARCHGUARD_CONFIG_ID)
                .setRefresh(true).request();
        GetResponse response = esClient.get(getRequest).actionGet();
        if (logger.isDebugEnabled()) {
            logger.debug("Read in roles {}", XContentHelper.convertToJson(response.getSourceAsBytesRef(), true, true));
        }
        return new SearchGuardRoles().load(response.getSource());
    }

    private SearchGuardRolesMapping readRolesMappingACL(Client esClient) throws IOException {
        GetRequest getRequest = esClient.prepareGet(searchGuardIndex, SEARCHGUARD_MAPPING_TYPE, SEARCHGUARD_CONFIG_ID)
                .setRefresh(true).request();
        GetResponse response = esClient.get(getRequest).actionGet();

        if (logger.isDebugEnabled()) {
            logger.debug("Read in rolesMapping {}",
                    XContentHelper.convertToJson(response.getSourceAsBytesRef(), true, true));
        }
        return new SearchGuardRolesMapping().load(response.getSource());
    }

    private void writeACL(Client esClient, SearchGuardRoles roles, SearchGuardRolesMapping rolesMapping)
            throws IOException {
        IndexRequest rolesIR = new IndexRequest(searchGuardIndex).type(SEARCHGUARD_ROLE_TYPE).id(SEARCHGUARD_CONFIG_ID)
                .refresh(true).consistencyLevel(WriteConsistencyLevel.DEFAULT).source(roles.toMap());
        if (logger.isDebugEnabled()) {
            logger.debug("Built roles request: {}", XContentHelper.convertToJson(rolesIR.source(), true, true));
        }
        String rolesID = esClient.index(rolesIR).actionGet().getId();
        logger.debug("Roles ID: '{}'", rolesID);

        IndexRequest mappingIR = new IndexRequest(searchGuardIndex).type(SEARCHGUARD_MAPPING_TYPE)
                .id(SEARCHGUARD_CONFIG_ID).refresh(true).consistencyLevel(WriteConsistencyLevel.DEFAULT)
                .source(rolesMapping.toMap());
        if (logger.isDebugEnabled()) {
            logger.debug("Built rolesMapping request: {}",
                    XContentHelper.convertToJson(mappingIR.source(), true, true));
        }
        String rmID = esClient.index(mappingIR).actionGet().getId();
        logger.debug("rolesMapping ID: '{}'", rmID);

        // force a config reload
        ConfigUpdateResponse cur = esClient
                .execute(ConfigUpdateAction.INSTANCE, new ConfigUpdateRequest(SEARCHGUARD_INITIAL_CONFIGS)).actionGet();

        if (cur.getNodes().length > 0) {
            logger.debug("Successfully reloaded config with '{}' nodes", cur.getNodes().length);
        } else {
            logger.warn("Failed to reloaded configs", cur.getNodes().length);
        }
    }

    @Override
    public int order() {
        // need to run before search guard
        return Integer.MIN_VALUE;
    }

    public boolean isClientCertAuth(final RestRequest request) {
        return (request != null) && request.hasInContext("_sg_ssl_principal")
                && StringUtils.isNotEmpty(request.getFromContext("_sg_ssl_principal", ""));
    }

    @SuppressWarnings("rawtypes")
    public void assertUser(RestRequest request) throws Exception {
        final String user = getUser(request);
        final String token = getBearerToken(request);
        ConfigBuilder builder = new ConfigBuilder().withOauthToken(token);
        try (DefaultOpenShiftClient osClient = new DefaultOpenShiftClient(builder.build())) {
            logger.debug("Verifying user {} matches the given token.", user);
            Request okRequest = new Request.Builder()
                    .addHeader(AUTHORIZATION_HEADER, "Bearer " + token)
                    .url(osClient.getMasterUrl() + "oapi/v1/users/~")
                    .build();
            String username = null;
            Response response = null;
            try {
                response = osClient.getHttpClient().newCall(okRequest).execute();
                final String body = response.body().string();
                if (logger.isDebugEnabled()) {
                    logger.debug("Response: code '{}' {}", response.code(), body);
                }
                if(response.code() != RestStatus.OK.getStatus()) {
                    throw new ElasticsearchSecurityException("", RestStatus.UNAUTHORIZED);
                }
                Map<String, Object> userResponse = XContentHelper.convertToMap(new BytesArray(body), false).v2();
                if(userResponse.containsKey("metadata") && ((Map)userResponse.get("metadata")).containsKey("name")) {
                    username = (String) ((Map)userResponse.get("metadata")).get("name");
                }
            }catch (Exception e) {
                logger.debug("Exception trying to assertUser '{}'", e, user);
                throw e;
            }
            if(!user.equals(username)) {
                String message = String.format("The username '%s' does not own the token provided with the request.", username);
                logger.debug(message);
                throw new ElasticsearchSecurityException("", RestStatus.UNAUTHORIZED);
            }
        }
    }
}
