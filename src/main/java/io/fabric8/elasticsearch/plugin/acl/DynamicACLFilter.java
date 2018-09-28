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

import java.util.function.UnaryOperator;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.threadpool.ThreadPool;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginSettings;
import io.fabric8.elasticsearch.plugin.kibana.KibanaSeed;
import io.fabric8.elasticsearch.plugin.rest.RestChannelInterceptor;
import io.fabric8.elasticsearch.util.RequestUtils;

/**
 * REST filter to update the ACL when a user first makes a request
 */
public class DynamicACLFilter implements ConfigurationSettings {

    private static final Logger LOGGER = Loggers.getLogger(DynamicACLFilter.class);
    private final String kibanaVersion;
    private final String kbnVersionHeader;
    private final String cdmProjectPrefix;
    private KibanaSeed kibanaSeed;
    private final ACLDocumentManager aclManager;
    private final RequestUtils utils;
    private final ThreadContext threadContext;
    private final String defaultKibanaIndex;

    public DynamicACLFilter(final PluginSettings settings, 
            final KibanaSeed seed, 
            final Client client, 
            final ThreadPool threadPool,
            final RequestUtils utils,
            final ACLDocumentManager aclManager) {
        this.threadContext = threadPool.getThreadContext();
        this.kibanaSeed = seed;
        this.kibanaVersion = settings.getKibanaVersion();
        this.kbnVersionHeader = settings.getKbnVersionHeader();
        this.cdmProjectPrefix = settings.getCdmProjectPrefix();
        this.defaultKibanaIndex = settings.getDefaultKibanaIndex();
        this.utils = utils;
        this.aclManager = aclManager;
    }

    /*
     * The hacky logic here is we assume authentication occurs using the authenticator
     * we provide.  This works first by allowing SG to use the authenticator to establish
     * a user and their roles.  We then dynamically update the ACL document, seed dashboards,
     * and modify the kibana uri if needed.   
     */
    public RestHandler wrap(final RestHandler original, final UnaryOperator<RestHandler> unaryOperator) {
        return new RestHandler() {
            
            private final RestHandler localHandler = new RestHandler() {
                
                @Override
                public void handleRequest(RestRequest request, RestChannel channel, NodeClient client) throws Exception {
                    if ((request = continueProcessing(request, channel)) != null) {
                        RestChannelInterceptor interceptor = new RestChannelInterceptor(channel, threadContext, defaultKibanaIndex);
                        original.handleRequest(request, interceptor, client);
                    }
                }
            };

            @Override
            public void handleRequest(RestRequest request, RestChannel channel, NodeClient client) throws Exception {
                
                RestHandler handler = unaryOperator.apply(localHandler);
                handler.handleRequest(request, channel, client);
            }
        };
    }

    public RestRequest continueProcessing(RestRequest request, RestChannel channel) throws Exception {
        try {
            if (threadContext.getTransient(OPENSHIFT_REQUEST_CONTEXT) != null) {
                OpenshiftRequestContext requestContext = threadContext.getTransient(OPENSHIFT_REQUEST_CONTEXT);
                request = utils.modifyRequest(request, requestContext, channel);
                if (requestContext != OpenshiftRequestContext.EMPTY) {
                    utils.logRequest(request);
                    final String kbnVersion = getKibanaVersion(request);
                    kibanaSeed.setDashboards(requestContext, kbnVersion, cdmProjectPrefix);
                    aclManager.syncAcl(requestContext);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error handling request", e);
        }
        return request;
    }


    private String getKibanaVersion(final RestRequest request) {
        String kbnVersion = StringUtils.defaultIfEmpty(request.header(kbnVersionHeader), "");
        if (StringUtils.isEmpty(kbnVersion)) {
            return this.kibanaVersion;
        }
        return kbnVersion;
    }

}
