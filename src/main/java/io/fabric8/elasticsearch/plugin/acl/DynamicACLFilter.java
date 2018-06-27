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

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.UnaryOperator;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.util.concurrent.ThreadContext.StoredContext;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.threadpool.ThreadPool;

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

    private final String kibanaVersion;

    private final String kbnVersionHeader;

    private Boolean enabled;

    private final String cdmProjectPrefix;

    private KibanaSeed kibanaSeed;

    private final ACLDocumentManager aclManager;
    private final OpenshiftRequestContextFactory contextFactory;
    private final RequestUtils utils;
    private final ThreadPool threadPool;

    public DynamicACLFilter(final PluginSettings settings, 
            final KibanaSeed seed, 
            final Client client, 
            final OpenshiftRequestContextFactory contextFactory,
            final ThreadPool threadPool,
            final RequestUtils utils,
            final ACLDocumentManager aclManager) {
        this.threadPool = threadPool;
        this.kibanaSeed = seed;
        this.contextFactory = contextFactory;
        this.kibanaVersion = settings.getKibanaVersion();
        this.kbnVersionHeader = settings.getKbnVersionHeader();
        this.cdmProjectPrefix = settings.getCdmProjectPrefix();
        this.enabled = settings.isEnabled();
        this.utils = utils;
        this.aclManager = aclManager;
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

    public RestRequest continueProcessing(RestRequest request, RestChannel channel, NodeClient client)
            throws Exception {
        try {
            OpenshiftRequestContext requestContext = OpenshiftRequestContext.EMPTY;
            try (StoredContext threadContext = threadPool.getThreadContext().stashContext()) {
                if (enabled) {
                    // create authenticates the request - if it returns null, this means
                    // this plugin cannot handle this request, and should pass it to the
                    // next plugin for processing e.g. client cert auth with no username/password
                    // if create throws an exception, it means there was an issue with the token
                    // and username and the request failed authentication
                    requestContext = contextFactory.create(request);
                    if (requestContext != OpenshiftRequestContext.EMPTY) {
                        request = utils.modifyRequest(request, requestContext, channel);
                        logRequest(request);
                        final String kbnVersion = getKibanaVersion(request);
                        kibanaSeed.setDashboards(requestContext, client, kbnVersion, cdmProjectPrefix);
                        aclManager.syncAcl(requestContext);
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

    private void logRequest(final RestRequest request) {
        if (LOGGER.isDebugEnabled()) {
            try {
                LOGGER.debug("Handling Request... {}", request.uri());
                String user = utils.getUser(request);
                String token = utils.getBearerToken(request);
                LOGGER.debug("Evaluating request for user '{}' with a {} token", user,
                        (StringUtils.isNotEmpty(token) ? "non-empty" : "empty"));
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
            return this.kibanaVersion;
        }
        return kbnVersion;
    }

}
