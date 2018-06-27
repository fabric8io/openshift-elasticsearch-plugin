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

import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.cluster.LocalNodeMasterListener;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.util.concurrent.FutureUtils;

import org.elasticsearch.threadpool.ThreadPool;

import io.fabric8.elasticsearch.plugin.acl.ACLDocumentManager;

/**
 * Service to handle spawning threads, lifecycles, and REST filter registrations
 * 
 */
public class OpenShiftElasticSearchService extends AbstractLifecycleComponent
        implements ConfigurationSettings, LocalNodeMasterListener {

    private static final Logger LOGGER = Loggers.getLogger(OpenShiftElasticSearchService.class);
    
    private final PluginSettings settings;
    private final ACLDocumentManager aclManager;
    private ScheduledThreadPoolExecutor scheduler;

    @SuppressWarnings("rawtypes")
    private ScheduledFuture scheduledFuture;

    public OpenShiftElasticSearchService( 
            final ACLDocumentManager aclManager, 
            final PluginSettings pluginSettings) {
        super(pluginSettings.getSettings());
        this.settings = pluginSettings;
        this.aclManager = aclManager;
    }
    
    @Override
    protected void doStart() throws ElasticsearchException {
        LOGGER.debug("Started");
    }

    @Override
    public void onMaster() {
        boolean dynamicEnabled = settings.isEnabled();
        LOGGER.debug("Starting with Dynamic ACL feature enabled: {}", dynamicEnabled);
        if (dynamicEnabled) {
            // expiration thread
            LOGGER.info("Starting the ACL expiration thread...");
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
        LOGGER.info("Stopping the ACL expiration thread...");
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
        LOGGER.debug("Stopped");
    }

    @Override
    protected void doClose() throws ElasticsearchException {
        offMaster();
        LOGGER.debug("Closed");
    }


}
