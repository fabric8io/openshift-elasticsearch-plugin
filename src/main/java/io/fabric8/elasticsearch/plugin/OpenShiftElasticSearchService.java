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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.util.concurrent.FutureUtils;
import org.elasticsearch.rest.RestController;

import io.fabric8.elasticsearch.plugin.acl.ACLNotifierService;
import io.fabric8.elasticsearch.plugin.acl.DynamicACLFilter;
import io.fabric8.elasticsearch.plugin.acl.UserProjectCache;

/**
 * Service to handle spawning threads, lifecycles,
 * and REST filter registrations
 * @author jeff.cantrill
 *
 */
public class OpenShiftElasticSearchService 
	extends AbstractLifecycleComponent<OpenShiftElasticSearchService>{


	private final ESLogger logger;
	private final UserProjectCache cache;
	private final Settings settings;
	private ScheduledThreadPoolExecutor scheduler;
	
	@SuppressWarnings("rawtypes")
	private ScheduledFuture scheduledFuture;
	private ExecutorService notifier;
	private ACLNotifierService aclNotifier;

	@Inject
	protected OpenShiftElasticSearchService(final Settings settings, final Client esClient, final RestController restController, 
			final UserProjectCache cache, final ACLNotifierService aclNotifer, final DynamicACLFilter aclFilter) {
		super(settings);
		this.settings = settings;
		this.logger = Loggers.getLogger(getClass(), settings);
		this.cache = cache;
		this.aclNotifier = aclNotifer;
		restController.registerFilter(aclFilter);
	}

	@Override
	protected void doStart() throws ElasticsearchException {
        //thread for ACL load callbacks
        logger.debug("Starting the acl notification thread...");
		notifier = Executors.newSingleThreadExecutor(
                EsExecutors.daemonThreadFactory(settings, "openshift_acl_notifier"));	
		aclNotifier.setExecutorService(notifier);
		
		//expiration thread
		logger.debug("Starting the expiration thread...");
        this.scheduler = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1,
                EsExecutors.daemonThreadFactory(settings, "openshift_elasticsearch_service"));
        this.scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        
        Runnable expire = new Runnable() {
			@Override
			public void run() {
				cache.expire();
			}
		};
        this.scheduledFuture = this.scheduler.scheduleWithFixedDelay(expire, 5, 60, TimeUnit.SECONDS);
		
		logger.debug("Started");
  
	}

	@Override
	protected void doStop() throws ElasticsearchException {
		if(notifier != null){
			notifier.shutdownNow();
		}		
		//cleanup expire thread
        FutureUtils.cancel(this.scheduledFuture);
        if(scheduler != null){
        	this.scheduler.shutdown();
        }
		logger.debug("Stopped");
	}

	@Override
	protected void doClose() throws ElasticsearchException {
        FutureUtils.cancel(this.scheduledFuture);
        if(this.scheduler != null){
        	this.scheduler.shutdown();
        }
		logger.debug("Closed");
	}

}
