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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.elasticsearch.common.logging.ESLogger;

import io.fabric8.openshift.api.model.NamedRoleBinding;
import io.fabric8.openshift.api.model.RoleBinding;

/**
 * Cache of users and projects to assist when updating and
 * writing the SearchGuard ACLs
 * @author jeff.cantrill
 *
 */
public class ProjectUserCache implements OpenShiftPolicyCache{
	
	private ESLogger logger;
	private Map<String, Set<String>> projectToUsers;
	private Map<String, Set<String>> userToProjects;
	
	public ProjectUserCache(ESLogger logger){
		this(logger, new ConcurrentHashMap<String, Set<String>>(), new ConcurrentHashMap<String, Set<String>>());
	}

	public ProjectUserCache(ESLogger logger, Map<String, Set<String>> projectToUsers, Map<String, Set<String>> userToProjects){
		this.logger = logger;
		this.projectToUsers = projectToUsers;
		this.userToProjects = userToProjects;
	}
	
	@Override
	public synchronized void update(final RoleBinding binding){
		final String project = binding.getMetadata().getNamespace();
		List<String> userNames = binding.getUserNames();
		Set<String> cachedUsers = projectToUsers.get(project);
		
		for (String user : userNames) {
			//added
			if(!cachedUsers.contains(user)){
				if(!userToProjects.containsKey(user)){
					userToProjects.put(user, new HashSet<String>());
				}
				userToProjects.get(user).add(project);
				logger.debug("Added '{}' user to cache", user);
			}
		}
		for (String cachedUser : cachedUsers) {
			//removed
			if(!userNames.contains(cachedUser)){
				userToProjects.get(cachedUser).remove(project);
				logger.debug("removed '{}' user from cache", cachedUser);
			}
		}
		
		cachedUsers.clear();
		cachedUsers.addAll(userNames);
		logger.debug("Added users '{}' to project cache '{}'", userNames , project);
		
		pruneCache(projectToUsers);
		pruneCache(userToProjects);
	}
	
	private void pruneCache(Map<String, Set<String>> map){
		for (Map.Entry<String, Set<String>> entry : new HashMap<>(map).entrySet()) {
			if(entry.getValue().isEmpty()){
				map.remove(entry.getKey());
			}
		}
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
