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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.DocWriteRequest.OpType;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.util.concurrent.ThreadContext.StoredContext;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.support.ConfigConstants;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginClient;
import io.fabric8.elasticsearch.plugin.PluginSettings;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRolesMapping.RolesMapping;

/**
 * Manages process of loading and updating the ACL Documents
 * for a user request 
 *
 */
public class ACLDocumentManager implements ConfigurationSettings {
    
    private static final String [] CONFIG_DOCS = {SEARCHGUARD_ROLE_TYPE, SEARCHGUARD_MAPPING_TYPE};
    private static final Logger LOGGER = Loggers.getLogger(ACLDocumentManager.class);
    private final ReentrantLock lock = new ReentrantLock();
    private final String searchGuardIndex;
    private final PluginClient client;
    private final SearchGuardSyncStrategyFactory documentFactory;
    private final ConfigurationLoader configLoader;
    private final ThreadContext threadContext;

    public ACLDocumentManager(final PluginClient client, final PluginSettings settings, final SearchGuardSyncStrategyFactory documentFactory, ThreadPool threadPool) {
        this.searchGuardIndex = settings.getSearchGuardIndex();
        this.client = client;
        this.documentFactory = documentFactory;
        this.threadContext = threadPool.getThreadContext();
        this.configLoader = new ConfigurationLoader(client.getClient(), threadPool, settings.getSettings());
    }
    
    @SuppressWarnings("rawtypes")
    interface ACLDocumentOperation{
        
        void execute(Collection<SearchGuardACLDocument> docs);
        
        BulkRequest buildRequest(Client client, BulkRequestBuilder builder, Collection<SearchGuardACLDocument> docs) throws IOException;
    }
    
    private void logContent(final String message, final String type, final ToXContent content) throws IOException{
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug(message, type, XContentHelper.toString(content));
        }
    }
    
    private void logDebug(final String message, final Object obj) {
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug(message, obj);
        }
    }
    
    @SuppressWarnings("rawtypes")
    class SyncAndExpireOperation implements ACLDocumentOperation {
        
        private SyncFromContextOperation sync;
        private ExpireOperation expire;
        
        SyncAndExpireOperation(OpenshiftRequestContext context){
            long now = System.currentTimeMillis();
            sync = new SyncFromContextOperation(context, now);
            expire = new ExpireOperation(now);
        }

        @Override
        public void execute(Collection<SearchGuardACLDocument> docs) {
            //purposely expire and then sync to add back in
            expire.execute(docs);
            sync.execute(docs);
        }

        @Override
        public BulkRequest buildRequest(Client client, BulkRequestBuilder builder,
                Collection<SearchGuardACLDocument> docs) throws IOException {
            return expire.buildRequest(client, builder, docs);
        }
        
    }
    
    @SuppressWarnings("rawtypes")
    class SyncFromContextOperation implements ACLDocumentOperation {

        private OpenshiftRequestContext context;
        private long now;

        public SyncFromContextOperation(OpenshiftRequestContext context, final long now) {
            this.context = context;
            this.now = now;
        }
        
        @Override
        public void execute(Collection<SearchGuardACLDocument> docs) {
            LOGGER.debug("Syncing from context to ACL...");
            for (SearchGuardACLDocument doc : docs) {
                if(ConfigurationSettings.SEARCHGUARD_MAPPING_TYPE.equals(doc.getType())){
                    RolesMappingSyncStrategy rolesMappingSync = documentFactory.createRolesMappingSyncStrategy((SearchGuardRolesMapping) doc, now);
                    rolesMappingSync.syncFrom(context);
                } else if(ConfigurationSettings.SEARCHGUARD_ROLE_TYPE.equals(doc.getType())) {
                    RolesSyncStrategy rolesSync = documentFactory.createRolesSyncStrategy((SearchGuardRoles) doc, now);
                    rolesSync.syncFrom(context);
                }
            }
        }

        @Override
        public BulkRequest buildRequest(Client client, BulkRequestBuilder builder, Collection<SearchGuardACLDocument> docs) throws IOException{
            
            for (SearchGuardACLDocument doc : docs) {
                logContent("Updating {} to: {}", doc.getType(), doc);
                Map<String, Object> content = new HashMap<>();
                content.put(doc.getType(), new BytesArray(XContentHelper.toString(doc)));
                UpdateRequestBuilder update = client
                        .prepareUpdate(searchGuardIndex, doc.getType(), SEARCHGUARD_CONFIG_ID)
                        .setDoc(content);
                if(doc.getVersion() != null) {
                    update.setVersion(doc.getVersion());
                }
                builder.add(update.request());
            }
            return builder.request();
        }
    }
    
    @SuppressWarnings("rawtypes")
    class ExpireOperation implements ACLDocumentOperation {

        private long now;

        public ExpireOperation(long currentTimeMillis) {
            this.now = currentTimeMillis;
        }

        @Override
        public void execute(Collection<SearchGuardACLDocument> docs) {
            LOGGER.debug("Expiring ACLs older then {}", now);
            for (SearchGuardACLDocument doc : docs) {
                if(ConfigurationSettings.SEARCHGUARD_MAPPING_TYPE.equals(doc.getType())){
                    SearchGuardRolesMapping mappings = (SearchGuardRolesMapping) doc;
                    for (RolesMapping mapping : mappings) {
                        //assume if the value is there its intentional
                        String expire = mapping.getExpire();
                        if(expire != null && NumberUtils.isNumber(expire) && Long.parseLong(expire) < now) {
                            logDebug("Expiring rolesMapping: {}", mapping);
                            mappings.removeRolesMapping(mapping);
                        }
                    }
                } else if(ConfigurationSettings.SEARCHGUARD_ROLE_TYPE.equals(doc.getType())) {
                    SearchGuardRoles roles = (SearchGuardRoles) doc;
                    for (Roles role : roles) {
                        //assume if the value is there its intentional
                        String expire = role.getExpire();
                        if(expire != null && Long.parseLong(expire) < now) {
                            logDebug("Expiring role: {}", role);
                            roles.removeRole(role);
                        }
                    }
                }
            }
        }

        @Override
        public BulkRequest buildRequest(Client client, BulkRequestBuilder builder, Collection<SearchGuardACLDocument> docs) throws IOException{
            for (SearchGuardACLDocument doc : docs) {
                logContent("Expired doc {} to be: {}", doc.getType(), doc);
                Map<String, Object> content = new HashMap<>();
                content.put(doc.getType(), new BytesArray(XContentHelper.toString(doc)));
                IndexRequestBuilder indexBuilder = client
                        .prepareIndex(searchGuardIndex, doc.getType(), SEARCHGUARD_CONFIG_ID)
                        .setOpType(OpType.INDEX)
                        .setVersion(doc.getVersion())
                        .setSource(content);
                builder.add(indexBuilder.request());
            }
            return builder.request();
        }
    }

    public void syncAcl(OpenshiftRequestContext context) {
        if(!syncAcl(new SyncAndExpireOperation(context))){
            LOGGER.warn("Unable to sync ACLs for request from user: {}", context.getUser());
        }
    }    
    
    private boolean syncAcl(ACLDocumentOperation operation) {
        //try up to 30 seconds and then continue
        for (int n : new int [] {1 , 1 , 2 , 3 , 5 , 8}) {
            if(trySyncAcl(operation)) {
                return true;
            }
            try {
                if(LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Sleeping for {}(s)", n);
                }
                Thread.sleep(n * 1000);
            } catch (InterruptedException e) {
                LOGGER.error("There was an error while trying the sleep the syncACL operation", e);
            }
        }
        return false;
    }
    
    public boolean trySyncAcl(ACLDocumentOperation operation) {
        LOGGER.debug("Syncing the ACL to ElasticSearch");
        try (StoredContext ctx = threadContext.stashContext()) {
            threadContext.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
            lock.lock();
            @SuppressWarnings("rawtypes")
            Collection<SearchGuardACLDocument> docs = loadAcls();
            if(docs.size() < 2) {
                return false;
            }
            operation.execute(docs);

            return isSuccessfulWrite(writeAcl(operation, docs));
        } catch (Exception e) {
            LOGGER.error("Exception while syncing ACL to Elasticsearch", e);
        } finally {
            lock.unlock();
        }
        return false;
    }
    
    @SuppressWarnings("rawtypes")
    private Collection<SearchGuardACLDocument> loadAcls() throws Exception {
        LOGGER.debug("Loading SearchGuard ACL...waiting up to 30s");
        Map<String, Tuple<Settings, Long>> loadedDocs = configLoader.load(CONFIG_DOCS, 30, TimeUnit.SECONDS);
        Collection<SearchGuardACLDocument> docs = new ArrayList<>(loadedDocs.size());
        for (Entry<String, Tuple<Settings, Long>> item : loadedDocs.entrySet()) {
            Settings settings = item.getValue().v1();
            Long version = item.getValue().v2();
            Map<String, Object> original = settings.getAsStructuredMap();
            if(LOGGER.isDebugEnabled()){
                logContent("Read in {}: {}", item.getKey(), settings);
            }
            switch (item.getKey()) {
            case SEARCHGUARD_ROLE_TYPE:
                docs.add(new SearchGuardRoles(version).load(original));
                break;
            case SEARCHGUARD_MAPPING_TYPE:
                docs.add(new SearchGuardRolesMapping(version).load(original));
                break;
            }
        }
        return docs;
    }
    
    @SuppressWarnings("rawtypes")
    private BulkResponse writeAcl(ACLDocumentOperation operation, Collection<SearchGuardACLDocument> docs) throws Exception {
        BulkRequestBuilder builder = client.getClient().prepareBulk().setRefreshPolicy(RefreshPolicy.WAIT_UNTIL);
        BulkRequest request = operation.buildRequest(this.client.getClient(), builder, docs);
        client.addCommonHeaders();
        return this.client.getClient().bulk(request).actionGet();
    }
    
    private boolean isSuccessfulWrite(BulkResponse response) {
        if(!response.hasFailures()) {
            ConfigUpdateRequest confRequest = new ConfigUpdateRequest(SEARCHGUARD_INITIAL_CONFIGS);
            client.addCommonHeaders();
            try {
                ConfigUpdateResponse cur = this.client.getClient().execute(ConfigUpdateAction.INSTANCE, confRequest).actionGet();
                final int totNodes = cur.getNodes().size();
                if (totNodes > 0) {
                    LOGGER.debug("Successfully reloaded config with '{}' nodes", totNodes);
                }else {
                    LOGGER.warn("Failed to reloaded configs", totNodes);
                }
            }catch(Exception e) {
                LOGGER.error("Unable to notify of an ACL config update", e);
            }
            return true;
        } else {
            LOGGER.debug("Unable to write ACL {}", response.buildFailureMessage());
        }
        return false;
    }
}
