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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.util.concurrent.ThreadContext.StoredContext;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.support.ConfigConstants;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginSettings;
import io.fabric8.elasticsearch.plugin.kibana.KibanaSeed;
import io.fabric8.elasticsearch.util.RequestUtils;

/**
 * REST filter to update the ACL when a user first makes a request
 */
public class DynamicACLFilter implements ConfigurationSettings {

    private static final Logger LOGGER = Loggers.getLogger(DynamicACLFilter.class);

    private final UserProjectCache cache;
    private final String searchGuardIndex;
    private final String kibanaVersion;
    private final ReentrantLock lock = new ReentrantLock();

    private final String kbnVersionHeader;

    private Boolean enabled;

    private final String cdmProjectPrefix;

    private KibanaSeed kibanaSeed;

    private final Client client;
    private final OpenshiftRequestContextFactory contextFactory;
    private final SearchGuardSyncStrategyFactory documentFactory;
    private final RequestUtils utils;
    private final ThreadPool threadPool;

    public DynamicACLFilter(final UserProjectCache cache, final PluginSettings settings, final KibanaSeed seed,
            final Client client, final OpenshiftRequestContextFactory contextFactory,
            final SearchGuardSyncStrategyFactory documentFactory, ThreadPool threadPool, final RequestUtils utils) {
        this.threadPool = threadPool;
        this.client = client;
        this.cache = cache;
        this.kibanaSeed = seed;
        this.contextFactory = contextFactory;
        this.documentFactory = documentFactory;
        this.searchGuardIndex = settings.getSearchGuardIndex();
        this.kibanaVersion = settings.getKibanaVersion();
        this.kbnVersionHeader = settings.getKbnVersionHeader();
        this.cdmProjectPrefix = settings.getCdmProjectPrefix();
        this.enabled = settings.isEnabled();
        this.utils = utils;
    }

    public RestHandler wrap(final RestHandler original, final UnaryOperator<RestHandler> unaryOperator) {
        return new RestHandler() {

            @Override
            public void handleRequest(RestRequest request, RestChannel channel, NodeClient client) throws Exception {
                if ((request = continueProcessing(request, channel, client)) != null) {
                    RestHandler handler = unaryOperator.apply(original);
                    handler.handleRequest(request, channel, client);
                }
            }
        };
    }

    public RestRequest continueProcessing(RestRequest request, RestChannel channel, NodeClient client) throws Exception {
        try {
            OpenshiftRequestContext requestContext = OpenshiftRequestContext.EMPTY;
            try (StoredContext threadContext = threadPool.getThreadContext().stashContext()) {
                if (enabled) {
                    // create authenticates the request - if it returns null, this means
                    // this plugin cannot handle this request, and should pass it to the
                    // next plugin for processing e.g. client cert auth with no username/password
                    // if create throws an exception, it means there was an issue with the token
                    // and username and the request failed authentication
                    requestContext = contextFactory.create(request, cache);
                    if (requestContext != OpenshiftRequestContext.EMPTY) {
                        request = utils.modifyRequest(request, requestContext, channel);
                        logRequest(request, cache);
                        final String kbnVersion = getKibanaVersion(request);
                        if (updateCache(requestContext, kbnVersion)) {
                            kibanaSeed.setDashboards(requestContext, client, kbnVersion, cdmProjectPrefix);
                            syncAcl(requestContext);
                        }
                    }
    
                }
            }
            threadPool.getThreadContext().putTransient(OPENSHIFT_REQUEST_CONTEXT, requestContext);
        } catch (ElasticsearchSecurityException ese) {
            LOGGER.info("Could not authenticate user");
            channel.sendResponse(new BytesRestResponse(RestStatus.UNAUTHORIZED, ""));
            request = null;
        } catch (Exception e) {
            LOGGER.error("Error handling request", e);
        }
        return request;
    }

    private void logRequest(final RestRequest request, final UserProjectCache cache) {
        if (LOGGER.isDebugEnabled()) {
            try {
                LOGGER.debug("Handling Request... {}", request.uri());
                String user = utils.getUser(request);
                String token = utils.getBearerToken(request);
                LOGGER.debug("Evaluating request for user '{}' with a {} token", user,
                        (StringUtils.isNotEmpty(token) ? "non-empty" : "empty"));
                LOGGER.debug("Cache has user: {}", cache.hasUser(user, token));
                if (LOGGER.isTraceEnabled()) {
                    List<String> headers = new ArrayList<>();
                    for (Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
                        if (RequestUtils.AUTHORIZATION_HEADER.equals(entry.getKey())) {
                            headers.add(entry.getKey() + "=Bearer <REDACTED>");
                        } else {
                            headers.add(entry.getKey() + "=" + entry.getValue());
                        }
                    }
                    LOGGER.trace("Request headers: {}", headers);
                }

            } catch (Exception e) {
                LOGGER.debug("unable to log request: " + e.getMessage());
            }
        }
    }

    private String getKibanaVersion(final RestRequest request) {
        String kbnVersion = StringUtils.defaultIfEmpty(request.header(kbnVersionHeader), "");
        if (StringUtils.isEmpty(kbnVersion)) {
            return kibanaVersion;
        }
        return kbnVersion;
    }

    private boolean updateCache(final OpenshiftRequestContext context, final String kbnVersion) {
        LOGGER.debug("Updating the cache for user '{}'", context.getUser());
        try {
            cache.update(context.getUser(), context.getToken(), context.getProjects(), context.isOperationsUser());
        } catch (Exception e) {
            LOGGER.error("Error updating cache for user '{}'", e, context.getUser());
            return false;
        }
        return true;
    }

    private <T extends SearchGuardACLDocument<T>> T loadSGConfig(T aclDocument) throws ElasticsearchParseException, IOException {
        GetResponse response = client.prepareGet(searchGuardIndex, aclDocument.getType(), SEARCHGUARD_CONFIG_ID).get();
        if(response.isExists()) {
            BytesReference bytes = response.getSourceAsBytesRef();
            try (XContentParser parser = XContentFactory.xContent(XContentFactory.xContentType(bytes)).createParser(NamedXContentRegistry.EMPTY, bytes.streamInput())) {
                
                parser.nextToken();
                parser.nextToken();
                
                if(!aclDocument.getType().equals((parser.currentName()))) {
                    return null;
                }
                
                parser.nextToken();
                
                XContentBuilder builder = XContentFactory.yamlBuilder();
                builder.rawValue(new BytesArray(parser.binaryValue()), XContentType.YAML);
                Map<String, Object> doc = XContentHelper.createParser(NamedXContentRegistry.EMPTY, new BytesArray(builder.string()), XContentType.YAML).map();
                return aclDocument.load(doc);
            }
            
        }else {
            LOGGER.error("There ACL {} document does not exist", response.getType()); 
        }
        return null;
    }
    
    private void syncAcl(OpenshiftRequestContext context) {
        LOGGER.debug("Syncing the ACL to ElasticSearch");
        try {
            lock.lock();
            LOGGER.debug("Loading SearchGuard ACL...");

            threadPool.getThreadContext().putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
            SearchGuardRoles roles = loadSGConfig(new SearchGuardRoles());
            SearchGuardRolesMapping rolesMapping = loadSGConfig(new SearchGuardRolesMapping());

            if (roles == null || rolesMapping == null) {
                return;
            }

            LOGGER.debug("Syncing from cache to ACL...");
            RolesMappingSyncStrategy rolesMappingSync = documentFactory.createRolesMappingSyncStrategy(rolesMapping);
            rolesMappingSync.syncFrom(cache);

            RolesSyncStrategy rolesSync = documentFactory.createRolesSyncStrategy(roles);
            rolesSync.syncFrom(cache);

            writeAcl(roles, rolesMapping);
        } catch (Exception e) {
            LOGGER.error("Exception while syncing ACL with cache", e);
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void writeAcl(SearchGuardACLDocument... documents) throws Exception {

        BulkRequestBuilder builder = this.client.prepareBulk().setRefreshPolicy(RefreshPolicy.IMMEDIATE);

        for (SearchGuardACLDocument doc : documents) {
            Map content = new HashMap();
            content.put(doc.getType(), doc.toXContentBuilder().bytes());
            UpdateRequest update = this.client.prepareUpdate(searchGuardIndex, doc.getType(), SEARCHGUARD_CONFIG_ID)
                    .setDoc(content).request();
            builder.add(update);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Built {} update request: {}", doc.getType(),
                        XContentHelper.convertToJson(doc.toXContentBuilder().bytes(), true, true, XContentType.JSON));
            }
        }
        BulkRequest request = builder.request();
        BulkResponse response = this.client.bulk(request).actionGet();

        if (!response.hasFailures()) {
            ConfigUpdateRequest confRequest = new ConfigUpdateRequest(SEARCHGUARD_INITIAL_CONFIGS);
            ConfigUpdateResponse cur = this.client.execute(ConfigUpdateAction.INSTANCE, confRequest).actionGet();
            final int size = cur.getNodes().size();
            if (size > 0) {
                LOGGER.debug("Successfully reloaded config with '{}' nodes", size);
            } else {
                LOGGER.warn("Failed to reloaded configs", size);
            }
        } else {
            LOGGER.error("Unable to write ACL {}", response.buildFailureMessage());
        }
    }
}
