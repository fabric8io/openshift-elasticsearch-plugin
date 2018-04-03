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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequestBuilder;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

import com.floragunn.searchguard.support.ConfigConstants;
import com.google.common.collect.UnmodifiableIterator;

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
    
    public UpdateResponse update(String index, String type, String id, String source) {

        LOGGER.debug("UPDATE: '{}/{}/{}' source: '{}'", index, type, id, source);

        UpdateRequestBuilder builder = client.prepareUpdate(index, type, id).setDoc(source).setDocAsUpsert(true);
        addCommonHeaders(builder);
        UpdateResponse response = builder.get();

        LOGGER.debug("Created with update? '{}'", response.isCreated());
        return response;
    }

    public IndexResponse createDocument(String index, String type, String id, String source) {
        LOGGER.trace("create document: '{}/{}/{}' source: '{}'", index, type, id, source);
        IndexRequestBuilder builder = client.prepareIndex(index, type, id).setSource(source);
        addCommonHeaders(builder);
        IndexResponse response = builder.get();
        return response;
    }
    
    public GetIndexResponse getIndices(String... indices) throws InterruptedException, ExecutionException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Getting indices '{}'", StringUtils.join(indices, ", "));
        }
        GetIndexRequestBuilder builder = client.admin().indices().prepareGetIndex().setIndices(indices);
        addCommonHeaders(builder);
        return builder.get();
    }
    
    public CreateIndexResponse copyIndex(final String source, final String target, String... types) throws InterruptedException, ExecutionException, IOException {
        LOGGER.trace("Copying {} index to {} for types {}", source, target, types);
        GetIndexResponse response = getIndices(source);
        CreateIndexRequestBuilder builder = client.admin().indices()
                .prepareCreate(target)
                .setSettings(response.getSettings().get(source));
        for (String type : types) {
            builder.addMapping(type, response.mappings().get(source).get(type).getSourceAsMap());
        }
        addCommonHeaders(builder);
        return builder.get();
    }
    
    public RefreshResponse refreshIndices(String... indices) {
        RefreshRequestBuilder builder = client.admin().indices().prepareRefresh(indices);
        addCommonHeaders(builder);
        RefreshResponse response = builder.get();
        LOGGER.debug("Refreshed '{}' successfully on {} of {} shards", indices, response.getSuccessfulShards(),
                response.getTotalShards());
        return response;
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
    
    public boolean documentExists(final String index, final String type, final String id) {
        LOGGER.trace("Checking for existence of document: '{}/{}/{}'", index, type, id);
        GetRequestBuilder builder = client.prepareGet()
            .setIndex(index)
            .setType(type)
            .setId(id)
            .setFields(new String[] {});
        addCommonHeaders(builder);
        GetResponse response = builder.get();
        final boolean exists = response.isExists();
        LOGGER.trace("Document '{}/{}/{}' exists? {}", index, type, id, exists);
        return exists;
    }
    
    /**
     * Retrieve the set of indices for a given alias
     * 
     * @param alias The alias to lookup
     * @return The set of indices to the given alias
     */
    public Set<String> getIndicesForAlias(String alias){
        LOGGER.trace("Retrieving indices for alias '{}'", alias);
        GetAliasesRequestBuilder builder = this.client.admin().indices().prepareGetAliases(alias);
        addCommonHeaders(builder);
        GetAliasesResponse response = builder.get();
        UnmodifiableIterator<String> keysIt = response.getAliases().keysIt();
        Set<String> indices = new HashSet<>();
        while (keysIt.hasNext()) {
            indices.add(keysIt.next());
        }
        LOGGER.trace("Indices for alias '{}': {}", alias, indices);
        return indices;
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
            LOGGER.trace("The alias map is empty.  Nothing to do");
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
