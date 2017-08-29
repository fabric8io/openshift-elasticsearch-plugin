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

/**
 * Strategy to sync between SearchGuard Documents and memory cache  
 *
 */
public interface RolesSyncStrategy {
    
    static final String[] USER_ROLE_CLUSTER_ACTIONS = { "USER_CLUSTER_OPERATIONS" };
    static final String[] PROJECT_ROLE_ACTIONS = { "INDEX_PROJECT" };
    static final String[] KIBANA_ROLE_ALL_INDEX_ACTIONS = { "INDEX_ANY_KIBANA" };
    static final String[] KIBANA_ROLE_INDEX_ACTIONS = { "INDEX_KIBANA" };
    static final String[] KIBANA_ROLE_CLUSTER_ACTIONS = { "CLUSTER_MONITOR_KIBANA" };
    static final String[] OPERATIONS_ROLE_CLUSTER_ACTIONS = { "CLUSTER_OPERATIONS" };
    static final String[] OPERATIONS_ROLE_OPERATIONS_ACTIONS = { "INDEX_OPERATIONS" };
    static final String[] OPERATIONS_ROLE_ANY_ACTIONS = { "INDEX_ANY_OPERATIONS" };
    static final String ALL = "*";

    /**
     * Sync the given cache to 
     * @param cache   The cache from which to sync
     */
    void syncFrom(final UserProjectCache cache);
    
}
