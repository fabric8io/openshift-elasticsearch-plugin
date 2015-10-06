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

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Singleton;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

@Singleton
public class DefaultACLNotifierService implements ACLNotifierService {

	private final Collection<SearchGuardACLActionRequestListener> listeners;
	private ExecutorService notifier;
	private ESLogger logger;

	@Inject
	public DefaultACLNotifierService(){
		this(Loggers.getLogger(DefaultACLNotifierService.class));
	}
	
	/*
	 * Testing Constructor
	 */
	public DefaultACLNotifierService(ESLogger logger){
		this.logger = logger;
		this.listeners = new ConcurrentLinkedQueue<>();
	}
	
	@Override
	public void notify(final String action) {
		if(notifier == null){
			logger.debug("Unable to notify callbacks because notifier thread is null");
			return;
		}
		if(listeners.isEmpty()){
			return;
		}
		notifier.execute(new Runnable() {
			@Override
			public void run() {
				logger.debug("Notifying {} listeners", listeners.size());
				Collection<SearchGuardACLActionRequestListener> receivers = new ArrayList<>(listeners);
				for (SearchGuardACLActionRequestListener listener : receivers) {
					try{
						listener.onSearchGuardACLActionRequest(action);
					}catch(Exception e){
						logger.error("There was an exception notifying a listener of a {} action against the SearchGuard ACL", e, action);
					}
				}
			}
		});
	}

	@Override
	public void addActionRequestListener(SearchGuardACLActionRequestListener listener){
		logger.debug("Added listener: {}", listeners.add(listener));
	}

	@Override
	public void removeActionRequestListener(SearchGuardACLActionRequestListener listener){
		logger.debug("Removed listener: {}", listeners.remove(listener));
	}
	
	@Override
	public void setExecutorService(ExecutorService service) {
		this.notifier = service;
	}

}
