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
	
	private String comment = SearchGuardACL.OPENSHIFT_SYNC;
	private Set<String> users = new HashSet<>();
	private Set<String> indexes = new HashSet<>();
	private Set<String> bypasses = new HashSet<>();
	private Set<String> executes = new HashSet<>();

	public Acl build(){
		Acl acl = new Acl();
		acl.setComment(new String(comment));
		acl.setIndices(new ArrayList<>(indexes));
		acl.setUsers(new ArrayList<>(users));
		acl.setFiltersBypass(new ArrayList<String>(bypasses));
		acl.setFiltersExecute(new ArrayList<String>(executes));
		return acl;
	}

	public AclBuilder user(String user) {
		users.add(user);
		return this;
	}
	
	public AclBuilder comment(String comment) {
		this.comment = new String(comment);
		return this;
	}

	public AclBuilder project(String project) {
		indexes.add(project);
		return this;
	}
	
	public AclBuilder bypass(String bypass) {
		bypasses.add(bypass);
		return this;
	}
	
	public AclBuilder executes(String execute) {
		executes.add(execute);
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
