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
import java.util.List;

import org.elasticsearch.common.logging.ESLogger;
import org.junit.Before;
import org.junit.Test;

import io.fabric8.openshift.api.model.NamedRoleBinding;
import io.fabric8.openshift.api.model.RoleBinding;

public class ProjectUserCacheTest {
	
	
	private static final String USERNAME = "ausername";
	private static final String PROJECT_NAME = "aprojectName";
	private ProjectUserCache cache;

	@Before
	public void setUp() throws Exception {
		ESLogger logger = mock(ESLogger.class);
		doNothing().when(logger).debug(anyString(),anyObject());
		cache = new ProjectUserCache(logger);
	}

	@Test
	public void addUsersShouldMapProjectToUsersAndUsersToProjects() {
		List<String> users = Arrays.asList(USERNAME);
		RoleBinding roles = spy(new RoleBinding());
		when(roles.getUserNames()).thenReturn(users);
		NamedRoleBinding binding = spy(new NamedRoleBinding());
		when(binding.getRoleBinding()).thenReturn(roles);
		
		cache.add(PROJECT_NAME, binding);
		
		assertArrayEquals(users.toArray(), cache.getUsersFor(PROJECT_NAME).toArray());
		assertArrayEquals(new String[]{PROJECT_NAME}, cache.getProjectsFor(USERNAME).toArray());
	}

	
}
