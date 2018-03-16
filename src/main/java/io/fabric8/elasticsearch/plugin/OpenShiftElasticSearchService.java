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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.LocalNodeMasterListener;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.util.concurrent.FutureUtils;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.threadpool.ThreadPool;

import io.fabric8.elasticsearch.plugin.acl.ACLDocumentManager;
import io.fabric8.elasticsearch.plugin.acl.DynamicACLFilter;

/**
 * Service to handle spawning threads, lifecycles, and REST filter registrations
 * 
 */
public class OpenShiftElasticSearchService extends AbstractLifecycleComponent<OpenShiftElasticSearchService>
        implements ConfigurationSettings, LocalNodeMasterListener {

    private final ESLogger logger;
    private final PluginSettings settings;
    private final ACLDocumentManager aclManager;
    private ScheduledThreadPoolExecutor scheduler;

    @SuppressWarnings("rawtypes")
    private ScheduledFuture scheduledFuture;

    @Inject
    public OpenShiftElasticSearchService(final ClusterService clusterService, 
            final Settings settings, 
            final Client esClient,
            final RestController restController, 
            final DynamicACLFilter aclFilter, 
            final ACLDocumentManager aclManager, 
            final PluginSettings pluginSettings) {
        super(settings);
        this.settings = pluginSettings;
        this.logger = Loggers.getLogger(getClass(), settings);
        restController.registerFilter(aclFilter);
        this.aclManager = aclManager;
        clusterService.add(this);
    }
    
    @Override
    protected void doStart() throws ElasticsearchException {
        logger.debug("Started");
    }

    @Override
    public void onMaster() {
        boolean dynamicEnabled = settings.isEnabled();
        logger.debug("Starting with Dynamic ACL feature enabled: {}", dynamicEnabled);
        if (dynamicEnabled) {
            // expiration thread
            logger.info("Starting the ACL expiration thread...");
            this.scheduler = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1,
                    EsExecutors.daemonThreadFactory(settings.getSettings(), "openshift_elasticsearch_service"));
            this.scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            this.scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);

            Runnable expire = new Runnable() {
                @Override
                public void run() {
                    aclManager.expire();
                }
            };
            this.scheduledFuture = this.scheduler.scheduleWithFixedDelay(expire, 1000 * 60, settings.getACLExpiresInMillis(), TimeUnit.MILLISECONDS);
        }
    }



    @Override
    public void offMaster() {
        // cleanup expire thread
        logger.info("Stopping the ACL expiration thread...");
        FutureUtils.cancel(this.scheduledFuture);
        if (scheduler != null) {
            this.scheduler.shutdown();
        }
    }
    
    @Override
    public String executorName() {
        return ThreadPool.Names.GENERIC;
    }

    @Override
    protected void doStop() throws ElasticsearchException {
        offMaster();
        logger.debug("Stopped");
    }

    @Override
    protected void doClose() throws ElasticsearchException {
        offMaster();
        logger.debug("Closed");
    }


}
