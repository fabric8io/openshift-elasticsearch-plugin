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

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestFilter;
import org.elasticsearch.rest.RestFilterChain;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginSettings;
import io.fabric8.elasticsearch.plugin.kibana.KibanaSeed;
import io.fabric8.elasticsearch.util.RequestUtils;

/**
 * REST filter to update the ACL when a user first makes a request
 */
public class DynamicACLFilter extends RestFilter implements ConfigurationSettings {

    private static final ESLogger LOGGER = Loggers.getLogger(DynamicACLFilter.class);

    private final String kibanaVersion;

    private final String kbnVersionHeader;

    private Boolean enabled;

    private final String cdmProjectPrefix;

    private KibanaSeed kibanaSeed;

    private final ACLDocumentManager aclManager;
    private final Client client;
    private final OpenshiftRequestContextFactory contextFactory;
    private final RequestUtils utils;

    @Inject
    public DynamicACLFilter(final PluginSettings settings, final KibanaSeed seed, 
            final Client client, final OpenshiftRequestContextFactory contextFactory,
            final RequestUtils utils,
            final ACLDocumentManager aclManager) {
        this.client = client;
        this.kibanaSeed = seed;
        this.contextFactory = contextFactory;
        this.kibanaVersion = settings.getKibanaVersion();
        this.kbnVersionHeader = settings.getKbnVersionHeader();
        this.cdmProjectPrefix = settings.getCdmProjectPrefix();
        this.enabled = settings.isEnabled();
        this.utils = utils;
        this.aclManager = aclManager;
    }

    @Override
    public void process(RestRequest request, RestChannel channel, RestFilterChain chain) throws Exception {
        boolean continueProcessing = true;

        try {
            if (enabled) {
                // create authenticates the request - if it returns null, this means
                // this plugin cannot handle this request, and should pass it to the
                // next plugin for processing e.g. client cert auth with no username/password
                // if create throws an exception, it means there was an issue with the token
                // and username and the request failed authentication
                final OpenshiftRequestContext requestContext = contextFactory.create(request);
                request = utils.modifyRequest(request, requestContext);
                if (requestContext == OpenshiftRequestContext.EMPTY) {
                    return; // do not process in this plugin
                }
                request.putInContext(OPENSHIFT_REQUEST_CONTEXT, requestContext);
                // grab the kibana version here out of "kbn-version" if we can
                // -- otherwise use the config one
                final String kbnVersion = getKibanaVersion(request);
                kibanaSeed.setDashboards(requestContext, client, kbnVersion, cdmProjectPrefix);
                aclManager.syncAcl(requestContext);
                    
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

    private String getKibanaVersion(RestRequest request) {
        String kbnVersion = (String) ObjectUtils.defaultIfNull(request.header(kbnVersionHeader), "");
        if (StringUtils.isEmpty(kbnVersion)) {
            return this.kibanaVersion;
        }
        return kbnVersion;
    }

    @Override
    public int order() {
        // need to run before search guard
        return Integer.MIN_VALUE;
    }
}
