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

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.elasticsearch.common.logging.ESLogger;


/**
 * A simple cache implementation for users->projects
 *  
 * @author jeff.cantrill
 *
 */
public class UserProjectCacheMapAdapter implements UserProjectCache {
	
	private final ESLogger logger;
	private final Map<String, Set<String>> cache = new ConcurrentHashMap<>();
	private final Map<String, Long> createTimes = new ConcurrentHashMap<>();
	private static final long EXPIRE = 1000 * 60; //1 MIN 

	public UserProjectCacheMapAdapter(final ESLogger logger){
		this.logger = logger;
	}
	
	@Override
	public Map<String, Set<String>> getUserProjects() {
		return Collections.unmodifiableMap(cache);
	}


	@Override
	public boolean hasUser(String user) {
		return cache.containsKey(user);
	}


	@Override
	public void update(final String user, final Set<String> projects) {
		cache.put(user, new HashSet<>(projects));
		createTimes.put(user, System.currentTimeMillis() + EXPIRE);
	}
	
	@Override
	public void expire(){
		final long now = System.currentTimeMillis();
		for (Map.Entry<String, Long> entry : new HashSet<>(createTimes.entrySet())) {
			if(now > entry.getValue()){
				logger.debug("Expiring cache entry for {}", entry.getKey());
				cache.remove(entry.getKey());
				createTimes.remove(entry.getKey());
			}
		}
	}

	
}
