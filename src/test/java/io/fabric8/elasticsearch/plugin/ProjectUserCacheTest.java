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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.common.logging.ESLogger;
import org.junit.Before;
import org.junit.Test;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.openshift.api.model.NamedRoleBinding;
import io.fabric8.openshift.api.model.RoleBinding;

public class ProjectUserCacheTest {
	
	
	private static final String USERNAME = "ausername";
	private static final String PROJECT_NAME = "aprojectName";
	private ProjectUserCache cache;
	private ESLogger logger;

	@Before
	public void setUp() throws Exception {
		logger = mock(ESLogger.class);
		doNothing().when(logger).debug(anyString(),anyObject());
	}

	@Test
	public void addUsersShouldMapProjectToUsersAndUsersToProjects() {
		List<String> users = Arrays.asList(USERNAME);
		RoleBinding roles = spy(new RoleBinding());
		when(roles.getUserNames()).thenReturn(users);
		NamedRoleBinding binding = spy(new NamedRoleBinding());
		when(binding.getRoleBinding()).thenReturn(roles);
		
		cache = new ProjectUserCache(logger);
		cache.add(PROJECT_NAME, binding);
		
		assertArrayEquals(users.toArray(), cache.getUsersFor(PROJECT_NAME).toArray());
		assertArrayEquals(new String[]{PROJECT_NAME}, cache.getProjectsFor(USERNAME).toArray());
	}

	@Test
	public void updateShouldAddUsersToProjectWhenUsersAreAdded(){
		Map<String, Set<String>> projectToUsers = new HashMap<String, Set<String>>();
		projectToUsers.put(PROJECT_NAME, new HashSet<>(Arrays.asList(USERNAME)));
		Map<String, Set<String>> usersToProjects = new HashMap<String, Set<String>>();
		usersToProjects.put(USERNAME, new HashSet<>(Arrays.asList(PROJECT_NAME)));
		
		cache = new ProjectUserCache(logger, projectToUsers, usersToProjects);
		
		final String ANOTHER_USER= "anotheruser";
		List<String> users = Arrays.asList(USERNAME, ANOTHER_USER);
		RoleBinding roles = givenARoleBindingFor(PROJECT_NAME, users);
		
		cache.update(roles);
		
		assertArrayEquals(users.toArray(), cache.getUsersFor(PROJECT_NAME).toArray());
		assertArrayEquals(new String[]{PROJECT_NAME}, cache.getProjectsFor(USERNAME).toArray());
		assertArrayEquals(new String[]{PROJECT_NAME}, cache.getProjectsFor(ANOTHER_USER).toArray());
		
	}

	@Test
	public void updateShouldRemoveUsersFromProjectWhenUsersAreRemoved(){
		final String ALT_PROJECT_NAME = "altprojectname";
		Map<String, Set<String>> projectToUsers = new HashMap<String, Set<String>>();
		projectToUsers.put(PROJECT_NAME, new HashSet<>(Arrays.asList(USERNAME)));
		projectToUsers.put(ALT_PROJECT_NAME, new HashSet<>(Arrays.asList(USERNAME)));
		Map<String, Set<String>> usersToProjects = new HashMap<String, Set<String>>();
		usersToProjects.put(USERNAME, new HashSet<>(Arrays.asList(PROJECT_NAME, ALT_PROJECT_NAME)));

		//these should get dropped
		HashSet<String> prunedUsers = new HashSet<String>(4);
		projectToUsers.put("athirdproject", prunedUsers);
		HashSet<String> prunedProjects = new HashSet<String>(5);
		usersToProjects.put("athirduser", prunedProjects);
		
		cache = new ProjectUserCache(logger, projectToUsers, usersToProjects);
		
		final String ANOTHER_USER= "anotheruser";
		List<String> users = Arrays.asList(ANOTHER_USER);
		RoleBinding roleBinding = givenARoleBindingFor(PROJECT_NAME, users);
		
		cache.update(roleBinding);
		
		assertArrayEquals(users.toArray(), cache.getUsersFor(PROJECT_NAME).toArray());
		assertArrayEquals(new String[]{PROJECT_NAME}, cache.getProjectsFor(ANOTHER_USER).toArray());
		
		assertArrayEquals(new String[]{USERNAME}, cache.getUsersFor(ALT_PROJECT_NAME).toArray());
		assertArrayEquals(new String[]{ALT_PROJECT_NAME}, cache.getProjectsFor(USERNAME).toArray());
		
		assertNotSame(prunedUsers, cache.getUsersFor("athirduser"));
		assertNotSame(prunedProjects, cache.getProjectsFor("athirdproject"));
		
	}
	
	private RoleBinding givenARoleBindingFor(String project, List<String> users){
		RoleBinding roles = spy(new RoleBinding());
		ObjectMeta meta = mock(ObjectMeta.class);
		when(meta.getNamespace()).thenReturn(PROJECT_NAME);
		when(roles.getMetadata()).thenReturn(meta);
		when(roles.getUserNames()).thenReturn(users);
		return roles;
	}
	
}
