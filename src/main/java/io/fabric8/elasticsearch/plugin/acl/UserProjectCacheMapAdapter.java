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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;

import io.netty.util.internal.ConcurrentSet;

/**
 * A simple cache implementation for users->projects
 * 
 * @author jeff.cantrill
 *
 */
public class UserProjectCacheMapAdapter implements UserProjectCache {

    private static final Logger logger = Loggers.getLogger(UserProjectCacheMapAdapter.class);
    private final Map<SimpleImmutableEntry<String, String>, Set<String>> cache = new ConcurrentHashMap<>();
    private final Map<SimpleImmutableEntry<String, String>, Long> createTimes = new ConcurrentHashMap<>();
    private final Map<SimpleImmutableEntry<String, String>, Boolean> operationsUsers = new ConcurrentHashMap<>();
    private final Set<String> projects = new ConcurrentSet<>();
    private static final long EXPIRE = 1000 * 60; // 1 MIN

    @Override
    public Map<SimpleImmutableEntry<String, String>, Set<String>> getUserProjects() {
        return Collections.unmodifiableMap(cache);
    }

    @Override
    public boolean hasUser(String user, String token) {
        SimpleImmutableEntry<String, String> sie = new SimpleImmutableEntry<>(user, token);
        return cache.containsKey(sie);
    }

    @Override
    public boolean isOperationsUser(String user, String token) {
        SimpleImmutableEntry<String, String> sie = new SimpleImmutableEntry<>(user, token);
        return operationsUsers.containsKey(sie) && operationsUsers.get(sie);
    }

    @Override
    public void update(final String user, String token, final Set<String> projects, boolean operationsUser) {
        SimpleImmutableEntry<String, String> sie = new SimpleImmutableEntry<>(user, token);
        cache.put(sie, new HashSet<>(projects));
        createTimes.put(sie, System.currentTimeMillis() + EXPIRE);
        operationsUsers.put(sie, operationsUser);
        this.projects.addAll(projects);
    }

    @Override
    public void expire() {
        final long now = System.currentTimeMillis();
        for (Map.Entry<SimpleImmutableEntry<String, String>, Long> entry : new HashSet<>(createTimes.entrySet())) {
            if (now > entry.getValue()) {
                logger.debug("Expiring cache entry for {}", entry.getKey());
                cache.remove(entry.getKey());
                createTimes.remove(entry.getKey());
                operationsUsers.remove(entry.getKey());
            }
        }
    }

    @Override
    public Set<String> getAllProjects() {
        return Collections.unmodifiableSet(projects);
    }
}
