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

import java.util.Map;

import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

import com.floragunn.searchguard.support.ConfigConstants;

/**
 * Facade to the ES client to simplify calls
 *
 */
public class PluginClient {

    private static ESLogger LOGGER = Loggers.getLogger(PluginClient.class);
    private final Client client;

    @Inject
    public PluginClient(Client client) {
        this.client = client;
    }

    public boolean indexExists(final String index) {
        LOGGER.trace("Checking for existance of index '{}'", index);
        IndicesExistsRequestBuilder builder = client.admin().indices().prepareExists(index);
        addCommonHeaders(builder);
        IndicesExistsResponse response = builder.get();
        boolean exists = response.isExists();
        LOGGER.trace("Index '{}' exists? {}", index, exists);
        return exists;
    }

    /**
     * Create an alias for a pattern
     * 
     * @param aliases
     *            a map of patterns to alias
     * @return true if the request was acknowledged
     */
    public boolean alias(Map<String, String> aliases) {
        boolean acknowledged = false;
        if (aliases.isEmpty()) {
            return acknowledged;
        }
        IndicesAliasesRequestBuilder builder = this.client.admin().indices().prepareAliases();
        addCommonHeaders(builder);
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            LOGGER.debug("Creating alias for {} as {}", entry.getKey(), entry.getValue());
            builder.addAlias(entry.getKey(), entry.getValue());
        }
        IndicesAliasesResponse response = builder.get();
        acknowledged = response.isAcknowledged();
        LOGGER.debug("Aliases request acknowledged? {}", acknowledged);
        return acknowledged;
    }

    @SuppressWarnings("rawtypes")
    private void addCommonHeaders(ActionRequestBuilder builder) {
        builder.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
    }
}
