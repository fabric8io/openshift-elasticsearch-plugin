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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.fabric8.elasticsearch.plugin.acl.SearchGuardACL.Acl;

public class AclBuilder {
	
	
	private Set<String> users = new HashSet<>();
	private Set<String> indexes = new HashSet<>();

	public Acl build(){
		Acl acl = new Acl();
		acl.setIndices(new ArrayList<>(indexes));
		acl.setUsers(new ArrayList<>(users));
		acl.setFiltersBypass(Arrays.asList("*"));
		acl.setFiltersExecute(new ArrayList<String>());
		return acl;
	}

	public AclBuilder user(String user) {
		users.add(user);
		return this;
	}

	public AclBuilder project(String project) {
		indexes.add(project);
		return this;
	}

	/**
	 * Completely replaces the list of projects
	 * @param projects
	 * @return
	 */
	public AclBuilder projects(List<String> projects) {
		indexes = new HashSet<>(projects);
		return this;
	}
}
