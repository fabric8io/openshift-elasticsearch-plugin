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

import java.util.Map;
import java.util.Set;

/**
 * Cache of SearchGuard ACLs
 *
 */
public interface UserProjectCache {

	/**
	 * Add users for a project 
	 * @param project  the project
	 * @param binding  the binding to get user info
	 */
	void update(final String user, Set<String> projects, boolean clusterAdmin);
	
	/**
	 * Retrieve an unmodifiable mapping of users to their projects
	 * @return
	 */
	Map<String, Set<String>> getUserProjects();
	
	/**
	 * 
	 * @param user
	 * @return true if the cache has an entry for a user
	 */
	boolean hasUser(String user);
	
	boolean isClusterAdmin(String user);
	
	void expire();
}
