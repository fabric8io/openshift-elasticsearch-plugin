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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.elasticsearch.common.logging.ESLogger;

import io.fabric8.openshift.api.model.NamedRoleBinding;

/**
 * Cache of users and projects to assist when updating and
 * writing the SearchGuard ACLs
 * @author jeff.cantrill
 *
 */
public class ProjectUserCache implements OpenShiftPolicyCache{
	
	private ESLogger logger;
	private Map<String, Set<String>> projectToUsers = new ConcurrentHashMap<>();
	private Map<String, Set<String>> userToProjects = new ConcurrentHashMap<>();
	

	public ProjectUserCache(ESLogger logger){
		this.logger = logger;
	}
	
	@Override
	public synchronized void add(final String project, NamedRoleBinding binding){
		List<String> userNames = binding.getRoleBinding().getUserNames();
		if(!projectToUsers.containsKey(project)){
			projectToUsers.put(project, new HashSet<String>(userNames.size()));
		};
		projectToUsers.get(project).addAll(userNames);
		addProjectForUsers(project, userNames);
		logger.debug("Adding {} to cache with names {} for role {}", project, userNames, binding.getName());

	}
	private void addProjectForUsers(final String project, List<String> userNames){
		for (String user : userNames) {
			if(!userToProjects.containsKey(user)){
				userToProjects.put(user, new HashSet<String>());
			}
			userToProjects.get(user).add(project);
		}
	}
	
	@Override
	public Set<String> getUsersFor(String project){
		if(!projectToUsers.containsKey(project)){
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(projectToUsers.get(project));
	}

	@Override
	public Set<String> getProjectsFor(String username) {
		if(!userToProjects.containsKey(username)){
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(userToProjects.get(username));
	}

	@Override
	public Map<String, Set<String>> getUserProjects() {
		return Collections.unmodifiableMap(userToProjects);
	}
}
