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
import java.util.concurrent.locks.ReentrantLock;

import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest.OpType;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.XContentHelper;

import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.support.ConfigConstants;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginSettings;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRoles.Roles;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRolesMapping.RolesMapping;

/**
 * Manages process of loading and updating the ACL Documents
 * for a user request 
 *
 */
public class ACLDocumentManager implements ConfigurationSettings {
    
    private static final ESLogger LOGGER = Loggers.getLogger(ACLDocumentManager.class);
    private final ReentrantLock lock = new ReentrantLock();
    private final String searchGuardIndex;
    private final Client client;
    private final SearchGuardSyncStrategyFactory documentFactory;

    @Inject
    public ACLDocumentManager( final Client client, final PluginSettings settings, final SearchGuardSyncStrategyFactory documentFactory) {
        this.searchGuardIndex = settings.getSearchGuardIndex();
        this.client = client;
        this.documentFactory = documentFactory;
    }
    
    interface ACLDocumentOperation{
        void syncDocuments(Collection<SearchGuardACLDocument> docs);
        
        BulkRequest buildRequest(Client client, BulkRequestBuilder builder, Collection<SearchGuardACLDocument> docs) throws IOException;
    }

    class SyncFromContextOperation implements ACLDocumentOperation {

        private OpenshiftRequestContext context;

        public SyncFromContextOperation(OpenshiftRequestContext context) {
            this.context = context;
        }
        
        @Override
        public void syncDocuments(Collection<SearchGuardACLDocument> docs) {
            LOGGER.debug("Syncing from context to ACL...");
            for (SearchGuardACLDocument doc : docs) {
                if(ConfigurationSettings.SEARCHGUARD_MAPPING_TYPE.equals(doc.getType())){
                    RolesMappingSyncStrategy rolesMappingSync = documentFactory.createRolesMappingSyncStrategy((SearchGuardRolesMapping) doc);
                    rolesMappingSync.syncFrom(context);
                } else if(ConfigurationSettings.SEARCHGUARD_ROLE_TYPE.equals(doc.getType())) {
                    RolesSyncStrategy rolesSync = documentFactory.createRolesSyncStrategy((SearchGuardRoles) doc);
                    rolesSync.syncFrom(context);
                }
            }
        }

        @Override
        public BulkRequest buildRequest(Client client, BulkRequestBuilder builder, Collection<SearchGuardACLDocument> docs) throws IOException{

            for (SearchGuardACLDocument doc : docs) {
                UpdateRequestBuilder update = client
                        .prepareUpdate(searchGuardIndex, doc.getType(), SEARCHGUARD_CONFIG_ID)
                        .setConsistencyLevel(WriteConsistencyLevel.DEFAULT)
                        .setDoc(doc.toXContentBuilder());
                if(doc.getVersion() != null) {
                    update.setVersion(doc.getVersion());
                }
                builder.add(update.request());
                if(LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Built {} update request: {}", doc.getType(), XContentHelper.convertToJson(doc.toXContentBuilder().bytes(),true, true));
                }
            }
            return builder.request();
        }
        
    }
    
    class ExpireOperation implements ACLDocumentOperation {

        private long now;

        public ExpireOperation(long currentTimeMillis) {
            this.now = currentTimeMillis;
        }

        @Override
        public void syncDocuments(Collection<SearchGuardACLDocument> docs) {
            LOGGER.debug("Expiring ACLs older then {}", now);
            for (SearchGuardACLDocument doc : docs) {
                if(ConfigurationSettings.SEARCHGUARD_MAPPING_TYPE.equals(doc.getType())){
                    SearchGuardRolesMapping mappings = (SearchGuardRolesMapping) doc;
                    for (RolesMapping mapping : mappings) {
                        //assume if the value is there its intentional
                        if(mapping.getExpire() != null && mapping.getExpire().longValue() < now) {
                            mappings.removeRolesMapping(mapping);
                        }
                    }
                } else if(ConfigurationSettings.SEARCHGUARD_ROLE_TYPE.equals(doc.getType())) {
                    SearchGuardRoles roles = (SearchGuardRoles) doc;
                    for (Roles role : roles) {
                        //assume if the value is there its intentional
                        if(role.getExpire() != null && role.getExpire().longValue() < now) {
                            roles.removeRole(role);
                        }
                    }
                }
            }
        }

        @Override
        public BulkRequest buildRequest(Client client, BulkRequestBuilder builder, Collection<SearchGuardACLDocument> docs) throws IOException{
            for (SearchGuardACLDocument doc : docs) {
                IndexRequestBuilder indexBuilder = client
                        .prepareIndex(searchGuardIndex, doc.getType(), SEARCHGUARD_CONFIG_ID)
                        .setConsistencyLevel(WriteConsistencyLevel.DEFAULT)
                        .setOpType(OpType.INDEX)
                        .setSource(doc.toXContentBuilder());
                builder.add(indexBuilder.request());
                if(LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Built {} index request: {}", doc.getType(), XContentHelper.convertToJson(doc.toXContentBuilder().bytes(),true, true));
                }
            }
            return builder.request();
        }
        
        
        
    }

    public void expire() {
        syncAcl(new ExpireOperation(System.currentTimeMillis()));
    }

    public void syncAcl(OpenshiftRequestContext context) {
        if(!syncAcl(new SyncFromContextOperation(context))){
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
                    LOGGER.trace("Sleeping for {}(s)", n * 1000);
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
        try {
            lock.lock();
            Collection<SearchGuardACLDocument> docs = loadAcls();
            if(docs.size() < 2) {
                return false;
            }
            operation.syncDocuments(docs);

            return isSuccessfulWrite(writeAcl(operation, docs));
        } catch (Exception e) {
            LOGGER.error("Exception while syncing ACL to Elasticsearch", e);
        } finally {
            lock.unlock();
        }
        return false;
    }
    
    
    
    private Collection<SearchGuardACLDocument> loadAcls() throws Exception {
        LOGGER.debug("Loading SearchGuard ACL...");

        final MultiGetRequest mget = new MultiGetRequest();
        mget.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true"); //header needed here
        mget.refresh(true);
        mget.realtime(true);
        mget.add(searchGuardIndex, SEARCHGUARD_ROLE_TYPE, SEARCHGUARD_CONFIG_ID);
        mget.add(searchGuardIndex, SEARCHGUARD_MAPPING_TYPE, SEARCHGUARD_CONFIG_ID);
        
        Collection<SearchGuardACLDocument> docs = new ArrayList<>(2);
        MultiGetResponse response = client.multiGet(mget).actionGet();
        Long version;
        for (MultiGetItemResponse item : response.getResponses()) {
            if(!item.isFailed()) {
                if(LOGGER.isDebugEnabled()){
                    LOGGER.debug("Read in {}: {}", item.getType(), XContentHelper.convertToJson(item.getResponse().getSourceAsBytesRef(), true, true));
                }
                switch (item.getType()) {
                case SEARCHGUARD_ROLE_TYPE:
                    version = item.getResponse().getVersion();
                    docs.add(new SearchGuardRoles(version).load(item.getResponse().getSource()));
                    break;
                case SEARCHGUARD_MAPPING_TYPE:
                    version = item.getResponse().getVersion();
                    docs.add(new SearchGuardRolesMapping(version).load(item.getResponse().getSource()));
                    break;
                }
            }else {
                LOGGER.error("There was a failure loading document type {}", item.getFailure(), item.getType());
            }
        }
        return docs;
    }
    
    private BulkResponse writeAcl(ACLDocumentOperation operation, Collection<SearchGuardACLDocument> docs) throws Exception {
        BulkRequestBuilder builder = client.prepareBulk().setRefresh(true);
        BulkRequest request = operation.buildRequest(this.client, builder, docs);
        request.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
        return this.client.bulk(request).actionGet();
    }
    
    private boolean isSuccessfulWrite(BulkResponse response) {
        if(!response.hasFailures()) {
            ConfigUpdateRequest confRequest = new ConfigUpdateRequest(SEARCHGUARD_INITIAL_CONFIGS);
            confRequest.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
            try {
                ConfigUpdateResponse cur = this.client.execute(ConfigUpdateAction.INSTANCE, confRequest).actionGet();
                if (cur.getNodes().length > 0) {
                    LOGGER.debug("Successfully reloaded config with '{}' nodes", cur.getNodes().length);
                }else {
                    LOGGER.warn("Failed to reloaded configs", cur.getNodes().length);
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
