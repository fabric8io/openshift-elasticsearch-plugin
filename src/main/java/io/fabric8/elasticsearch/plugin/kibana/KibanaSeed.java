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
package io.fabric8.elasticsearch.plugin.kibana;

import static io.fabric8.elasticsearch.plugin.KibanaUserReindexFilter.getUsernameHash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.search.SearchHit;

public class KibanaSeed {
	
	private static ESLogger logger = Loggers.getLogger(KibanaSeed.class);
	
	private static final String DEFAULT_INDEX_TYPE = "config";
	private static final String SEARCH_TYPE = "search";
	private static final String SEARCH_ID = "properties";
	private static final String INDICIES_TYPE = "index-pattern";
	
	private static final String OPERATIONS_PROJECT = ".operations";
	private static final String BLANK_PROJECT = ".empty-project";
	
	//TODO: should these be able to be read from property values?
	private static final String[] OPERATIONS_ROLES = {"cluster-admin"};
	private static final String[] BLACKLIST_PROJECTS = {"default", "openshift", "openshift-infra" };
	
	public static final String DEFAULT_INDEX_FIELD = "defaultIndex";
	
	public static void setDashboards(String user, Set<String> projects, Set<String> roles, Client esClient, String kibanaIndex, String kibanaVersion) {

		//GET .../.kibana/index-pattern/_search?pretty=true&fields=
		//  compare results to projects; handle any deltas (create, delete?)
		//check projects for default, openshift, openshift-infra and remove
		for ( String project : BLACKLIST_PROJECTS )
			if ( projects.contains(project) ) {
				logger.debug("Black-listed project '{}' found.  Not adding as an index pattern", project);
				projects.remove(project);
			}
		
		Set<String> indexPatterns = getIndexPatterns(user, esClient, kibanaIndex);
		logger.debug("Found '{}' Index patterns for user", indexPatterns.size());
		
		// Check roles here, if user is a cluster-admin we should add .operations to their project? -- correct way to do this?
		logger.debug("Checking for '{}' in users roles '{}'", OPERATIONS_ROLES, roles);
		for ( String role : OPERATIONS_ROLES )
			if ( roles.contains(role) ) {
				logger.debug("{} is an admin user", user);
				projects.add(OPERATIONS_PROJECT);
				break;
			}
		
		List<String> sortedProjects = new ArrayList<String>(projects);
		Collections.sort(sortedProjects);
		
		if ( sortedProjects.isEmpty() )
			sortedProjects.add(BLANK_PROJECT);
		
		logger.debug("Setting dashboards given user '{}' and projects '{}'", user, projects);
		
		// If none have been set yet
		if ( indexPatterns.isEmpty() ) {
			create(user, sortedProjects, true, esClient, kibanaIndex, kibanaVersion);
		}
		else {
			List<String> common = new ArrayList<String>(indexPatterns);
			
			// Get a list of all projects that are common
			common.retainAll(sortedProjects);
			
			sortedProjects.removeAll(common);
			indexPatterns.removeAll(common);
			
			// for any to create (remaining in projects) call createIndices, createSearchmapping?, create dashboard
			create(user, sortedProjects, false, esClient, kibanaIndex, kibanaVersion);
			
			// cull any that are in ES but not in OS (remaining in indexPatterns)
			remove(user, indexPatterns, esClient, kibanaIndex);
			
			common.addAll(sortedProjects);
			Collections.sort(common);
			// Set default index to first index in common if we removed the default
			String defaultIndex = getDefaultIndex(user, esClient, kibanaIndex, kibanaVersion);
			
			logger.debug("Checking if '{}' contains '{}'", indexPatterns, defaultIndex);
			
			if ( indexPatterns.contains(defaultIndex) || StringUtils.isEmpty(defaultIndex) ) {
				logger.debug("'{}' does contain '{}' and common size is {}", indexPatterns, defaultIndex, common.size());
				if ( common.size() > 0 )
					setDefaultIndex(user, common.get(0), esClient, kibanaIndex, kibanaVersion);
			}
			
		}
	}
	
	// this may return other than void later...
	private static void setDefaultIndex(String username, String project, Client esClient, String kibanaIndex, String kibanaVersion) {
		// this will create a default index of [index.]YYYY.MM.DD in .kibana.username
		String source = new DocumentBuilder()
				.defaultIndex(getIndexPattern(project))
				.build();
		
		executeCreate(getKibanaIndex(username, kibanaIndex), DEFAULT_INDEX_TYPE, kibanaVersion, source, esClient);
	}
	
	private static String getDefaultIndex(String username, Client esClient, String kibanaIndex, String kibanaVersion) {
		GetRequest request = esClient.prepareGet(getKibanaIndex(username, kibanaIndex), DEFAULT_INDEX_TYPE, kibanaVersion).request();
		
		try {
			GetResponse response = esClient.get(request).get();
			
			Map<String, Object> source = response.getSource();
			
			if ( source.containsKey(DEFAULT_INDEX_FIELD) ) {
				logger.debug("Received response with 'defaultIndex' = {}", source.get(DEFAULT_INDEX_FIELD));
				String index = (String) source.get(DEFAULT_INDEX_FIELD);
				
				return getProjectFromIndex(index);
			}
			else {
				logger.debug("Received response without 'defaultIndex'");
			}
			
		} catch (InterruptedException | ExecutionException e) {
			
			if ( e.getCause() instanceof org.elasticsearch.indices.IndexMissingException ) {
				logger.debug("No index found");
			}
			else {
				logger.error("Error getting default index for {}", e, username);
			}
		}
		
		return "";
	}
	
	private static void create(String user, List<String> projects, boolean setDefault, Client esClient, String kibanaIndex, String kibanaVersion) {
		boolean defaultSet = !setDefault;
		
		for ( String project: projects ) {
			createIndex(user, project, esClient, kibanaIndex);
			
			//set default
			if ( !defaultSet ) {
				setDefaultIndex(user, project, esClient, kibanaIndex, kibanaVersion);
				defaultSet = true;
			}
		}
	}
	
	private static void remove(String user, Set<String> projects, Client esClient, String kibanaIndex) {
		
		for ( String project: projects ) {
			deleteIndex(user, project, esClient, kibanaIndex);
		}
	}
	
	// This is a mis-nomer... it actually returns the project name of index patterns (.operations included)
	private static Set<String> getIndexPatterns(String username, Client esClient, String kibanaIndex) {

		Set<String> patterns = new HashSet<String>();
		
		SearchRequest request = esClient.prepareSearch(getKibanaIndex(username, kibanaIndex))
				.setTypes(INDICIES_TYPE)
				.request();
		
		try {
			SearchResponse response = esClient.search(request).get();
			
			if ( response.getHits() != null && response.getHits().getTotalHits() > 0 )
				for ( SearchHit hit : response.getHits().getHits() ) {
					String id = hit.getId();
					String project = getProjectFromIndex(id);

					if ( !project.equals(id) )
						patterns.add(project);

					// else -> this is user created, leave it alone
				}
				
		} catch (InterruptedException | ExecutionException e) {
			// if is ExecutionException with cause of IndexMissingException
			if ( e.getCause() instanceof org.elasticsearch.indices.IndexMissingException ) {
				logger.debug("Encountered IndexMissingException, returning empty response");
			}
			else {
				logger.error("Error getting index patterns for {}", e, username);
			}
		}
		
		return patterns;
	}
	
	private static void createIndex(String username, String project, Client esClient, String kibanaIndex) {
		
		DocumentBuilder sourceBuilder = new DocumentBuilder()
				.title(getIndexPattern(project))
				.timeFieldName("time");
		
		if ( project.equalsIgnoreCase(OPERATIONS_PROJECT) )
			sourceBuilder.operationsFields();
		else if ( project.equalsIgnoreCase(BLANK_PROJECT) )
			sourceBuilder.blankFields();
		else
			sourceBuilder.applicationFields();
		
		String source = sourceBuilder.build();
		
		executeCreate(getKibanaIndex(username, kibanaIndex), INDICIES_TYPE, getIndexPattern(project), source, esClient);
	}
	
	private static void deleteIndex(String username, String project, Client esClient, String kibanaIndex) {
		
		executeDelete(getKibanaIndex(username, kibanaIndex), INDICIES_TYPE, getIndexPattern(project), esClient);
	}
	
	private static void executeCreate(String index, String type, String id, String source, Client esClient) {
		
		logger.debug("CREATE: '{}/{}/{}' source: '{}'", index, type, id, source);
		
		IndexRequest request = esClient.prepareIndex(index, type, id).setSource(source).request();
		try {
			esClient.index(request).get();
		} catch (InterruptedException | ExecutionException e) {
			// Avoid printing out any kibana specific information?
			logger.error("Error executing create request", e);
		}
	}
	
	private static void executeDelete(String index, String type, String id, Client esClient) {
		
		logger.debug("DELETE: '{}/{}/{}'", index, type, id);
		
		DeleteRequest request = esClient.prepareDelete(index, type, id).request();
		try {
			esClient.delete(request).get();
		} catch (InterruptedException | ExecutionException e) {
			// Avoid printing out any kibana specific information?
			logger.error("Error executing delete request", e);
		}
	}
	
	private static String getKibanaIndex(String username, String kibanaIndex) {
		return kibanaIndex + "." + getUsernameHash(username);
	}
	
	private static String getIndexPattern(String project) {
		return project + ".*";
	}
	
	private static String getProjectFromIndex(String index) {
		
		if ( !StringUtils.isEmpty(index) ) {
			int wildcard = index.indexOf('*');

			if ( wildcard > 0 )
				return index.substring(0, wildcard - 1);
		}
			
		return index;
	}
}
