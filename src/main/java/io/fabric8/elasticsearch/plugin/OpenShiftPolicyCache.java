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

import java.util.Map;
import java.util.Set;

import io.fabric8.openshift.api.model.NamedRoleBinding;
import io.fabric8.openshift.api.model.RoleBinding;

/**
 * Cache of SearchGuard ACLs
 *
 */
public interface OpenShiftPolicyCache {

	/**
	 * Add users for a project 
	 * @param project  the project
	 * @param binding  the binding to get user info
	 */
	void add(final String project, NamedRoleBinding binding);
	
	/**
	 * Update the cache using the given binding
	 * 
	 * @param project
	 * @param binding
	 */
	void update(RoleBinding binding);
	
	/**
	 * Retrieve an unmodifiable mapping of users to their projects
	 * @return
	 */
	Map<String, Set<String>> getUserProjects();
	
	/**
	 * the users that have access to a project 
	 * @param project
	 * @return an Unmodifiable set or an empty set if the cache doesn't
	 *         know about the project
	 */
	Set<String> getUsersFor(String project);
	
	Set<String> getProjectsFor(String username);
}
