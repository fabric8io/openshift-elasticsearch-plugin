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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.util.concurrent.ThreadContext.StoredContext;
import org.elasticsearch.common.xcontent.XContentType;

import com.floragunn.searchguard.support.ConfigConstants;

/**
 * Facade to the ES client to simplify calls
 *
 */
public class PluginClient {

    private static Logger LOGGER = Loggers.getLogger(PluginClient.class);
    private final Client client;
    private final ThreadContext threadContext;

    public PluginClient(Client client, ThreadContext threadContext) {
        this.client = client;
        this.threadContext = threadContext;
    }

    public void deleteDocument(String index, String type, String id) {
        execute(new Callable<Object>() {

            @Override
            public Object call() throws Exception {
                LOGGER.debug("Deleted document: '{}/{}/{}'", index, type, id);
                addCommonHeaders();
                DeleteResponse response = client.prepareDelete(index, type, id).get();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Document deleted: '{}'", response.status());
                }
                return null;
            }
            
        });
    }
    
    public void updateDocument(String index, String type, String id, String source) {
        execute(new Callable<Object>() {

            @Override
            public Object call() throws Exception {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Updating Document: '{}/{}/{}' source: '{}'", index, type, id, source);
                }
                addCommonHeaders();
                UpdateResponse response = client.prepareUpdate(index, type, id)
                        .setDoc(source, XContentType.JSON)
                        .setDocAsUpsert(true)
                        .get();
                
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Document Updated: '{}'", response.status());
                }
                return null;
            }
        });
    }
    
    public SearchResponse search(String index, String type) {
        return search(new String []{index}, new String [] {type}); 
    }
    
    public SearchResponse search(String [] indicies, String [] types) {
        return execute(new Callable<SearchResponse>() {

            @Override
            public SearchResponse call() throws Exception {
                addCommonHeaders();
                return client.prepareSearch(indicies).setTypes(types).get();
            }
            
        });
    }
    
    public GetIndexResponse getIndex(String... indicies) {
        return execute(new Callable<GetIndexResponse>() {
            @Override
            public GetIndexResponse call() throws Exception {
                addCommonHeaders();
                return client.admin().indices().prepareGetIndex().addIndices(indicies).get();
            }
        });
    }
    
    public GetResponse getDocument(String index, String type, String id) {
        return execute(new Callable<GetResponse>() {

            @Override
            public GetResponse call() throws Exception {
                addCommonHeaders();
                return client
                        .prepareGet(index, type, id)
                        .get();
            }
        });
    }
    
    public IndexResponse createDocument(String index, String type, String id, String source) {
        return execute(new Callable<IndexResponse>() {

            @Override
            public IndexResponse call() throws Exception {
                LOGGER.trace("create document: '{}/{}/{}' source: '{}'", index, type, id, source);
                IndexRequestBuilder builder = client.prepareIndex(index, type, id).setSource(source, XContentType.JSON);
                addCommonHeaders();
                IndexResponse response = builder.get();
                return response;
            }
        });
    }

    public boolean indexExists(final String index) {
        return execute(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                LOGGER.trace("Checking for existance of index '{}'", index);
                IndicesExistsRequestBuilder builder = client.admin().indices().prepareExists(index);
                addCommonHeaders();
                IndicesExistsResponse response = builder.get();
                boolean exists = response.isExists();
                LOGGER.trace("Index '{}' exists? {}", index, exists);
                return exists;
            }
        });
    }
    
    public boolean documentExists(final String index, final String type, final String id) {
        return execute(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                LOGGER.trace("Checking for existence of document: '{}/{}/{}'", index, type, id);
                GetRequestBuilder builder = client.prepareGet()
                        .setIndex(index)
                        .setType(type)
                        .setId(id);
                addCommonHeaders();
                GetResponse response = builder.get();
                final boolean exists = response.isExists();
                LOGGER.trace("Document '{}/{}/{}' exists? {}", index, type, id, exists);
                return exists;
            }
        });
    }
    
    /**
     * Retrieve the set of indices for a given alias
     * 
     * @param alias The alias to lookup
     * @return The set of indices to the given alias
     */
    public Set<String> getIndicesForAlias(String alias){
        return execute(new Callable<Set<String>>() {

            @Override
            public Set<String> call() throws Exception {
                LOGGER.trace("Retrieving indices for alias '{}'", alias);
                GetAliasesRequestBuilder builder = client.admin().indices().prepareGetAliases(alias);
                addCommonHeaders();
                GetAliasesResponse response = builder.get();
                Iterator<String> keysIt = response.getAliases().keysIt();
                Set<String> indices = new HashSet<>();
                while (keysIt.hasNext()) {
                    indices.add(keysIt.next());
                }
                LOGGER.trace("Indices for alias '{}': {}", alias, indices);
                return indices;
            }
        });
    }

    /**
     * Create an alias for a pattern
     * 
     * @param aliases
     *            a map of patterns to alias
     * @return true if the request was acknowledged
     */
    public boolean alias(Map<String, String> aliases) {
        return execute(new Callable<Boolean>(){

            @Override
            public Boolean call() throws Exception {
                boolean acknowledged = false;
                if (aliases.isEmpty()) {
                    LOGGER.trace("The alias map is empty.  Nothing to do");
                    return acknowledged;
                }
                IndicesAliasesRequestBuilder builder = client.admin().indices().prepareAliases();
                addCommonHeaders();
                for (Map.Entry<String, String> entry : aliases.entrySet()) {
                    LOGGER.debug("Creating alias for {} as {}", entry.getKey(), entry.getValue());
                    builder.addAlias(entry.getKey(), entry.getValue());
                }
                IndicesAliasesResponse response = builder.get();
                acknowledged = response.isAcknowledged();
                LOGGER.debug("Aliases request acknowledged? {}", acknowledged);
                return acknowledged;
            }
        });
    }

    private void addCommonHeaders() {
        if(StringUtils.isBlank(threadContext.getTransient(ConfigConstants.SG_CHANNEL_TYPE))) {
            threadContext.putTransient(ConfigConstants.SG_CHANNEL_TYPE, "direct");
        }
    }
    
    private <T> T execute(Callable<T> callable) {
        try (StoredContext context = threadContext.stashContext()) {
            threadContext.putTransient(ConfigConstants.SG_CHANNEL_TYPE, "direct");
            return callable.call();
        } catch (Exception e) {
            throw new ElasticsearchException(e);
        }
    }
    
}
