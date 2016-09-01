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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.transport.RemoteTransportException;

public class KibanaSeed {
	
	private static ESLogger logger = Loggers.getLogger(KibanaSeed.class);
	
	private static final String DEFAULT_INDEX_TYPE = "config";
	private static final String INDICIES_TYPE = "index-pattern";
	
	private static final String OPERATIONS_PROJECT = ".operations";
	private static final String BLANK_PROJECT = ".empty-project";
	private static final String ADMIN_ALIAS_NAME = ".all";
	
	//TODO: should these be able to be read from property values?
	private static final String[] OPERATIONS_ROLES = {"cluster-admin"};
	
	public static final String DEFAULT_INDEX_FIELD = "defaultIndex";
	
	public static void setDashboards(String user, Set<String> projects, Set<String> roles, Client esClient, String kibanaIndex, String kibanaVersion) {

		//We want to seed the Kibana user index intially
		// since the logic from Kibana has changed to create before this plugin
		// starts...
		AtomicBoolean changed = new AtomicBoolean(initialSeedKibanaIndex(user, kibanaIndex, esClient));
		
		boolean isAdmin = false;
		//GET .../.kibana/index-pattern/_search?pretty=true&fields=
		//  compare results to projects; handle any deltas (create, delete?)
		
		Set<String> indexPatterns = getIndexPatterns(user, esClient, kibanaIndex);
		logger.debug("Found '{}' Index patterns for user", indexPatterns.size());
		
		// Check roles here, if user is a cluster-admin we should add .operations to their project? -- correct way to do this?
		logger.debug("Checking for '{}' in users roles '{}'", OPERATIONS_ROLES, roles);
		for ( String role : OPERATIONS_ROLES )
			if ( roles.contains(role) ) {
				logger.debug("{} is an admin user", user);
				projects.add(OPERATIONS_PROJECT);
				isAdmin = true;
				projects.add(ADMIN_ALIAS_NAME);
				break;
			}
		
		List<String> sortedProjects = new ArrayList<String>(projects);
		Collections.sort(sortedProjects);
		
		if ( sortedProjects.isEmpty() )
			sortedProjects.add(BLANK_PROJECT);
		
		logger.debug("Setting dashboards given user '{}' and projects '{}'", user, projects);
		
		if ( isAdmin ) {
			logger.debug("Adding to alias for {}", user);
			buildAdminAlias(user, sortedProjects, esClient, kibanaIndex, kibanaVersion);
		}
		
		// If none have been set yet
		if ( indexPatterns.isEmpty() ) {
			create(user, sortedProjects, true, esClient, kibanaIndex, kibanaVersion);
			changed.set(true);
		}
		else {
			List<String> common = new ArrayList<String>(indexPatterns);
			
			common.retainAll(sortedProjects);
			
			sortedProjects.removeAll(common);
			indexPatterns.removeAll(common);

			// if we aren't a cluster-admin, make sure we're deleting the ADMIN_ALIAS_NAME
			if ( !isAdmin ) {
				logger.debug("user is not a cluster admin, ensure they don't keep/have the admin alias pattern");
				indexPatterns.add(ADMIN_ALIAS_NAME);
			}

			//check if we're going to be adding or removing any projects
			if ( sortedProjects.size() > 0 ||
					indexPatterns.size() > 0 ) {
				changed.set(true);
			}
			
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
		
		if ( changed.get() )
			refreshKibanaUser(user, kibanaIndex, esClient);
	}
	
	private static void refreshKibanaUser(String username, String kibanaIndex, Client esClient) {
		
		String userIndex = getKibanaIndex(username, kibanaIndex);
		RefreshRequest request = new RefreshRequest().indices(userIndex);
		RefreshResponse response = esClient.admin().indices().refresh(request).actionGet();
		
		logger.debug("Refreshed '{}' successfully on {} of {} shards", userIndex, response.getSuccessfulShards(), response.getTotalShards());
	}
	
	private static boolean initialSeedKibanaIndex(String username, String kibanaIndex, Client esClient) {

		try {	
			String userIndex = getKibanaIndex(username, kibanaIndex);
			IndicesExistsResponse existsResponse = esClient.admin()
					.indices().prepareExists(userIndex).get();
			
			logger.debug("Checking if index {} exists? {}", userIndex, existsResponse.isExists());
			
			if ( !existsResponse.isExists() ) {
				logger.debug("Copying '{}' to '{}'", kibanaIndex, userIndex);
				
				GetIndexRequest getRequest = new GetIndexRequest()
						.indices(kibanaIndex);
				GetIndexResponse getResponse = esClient.admin()
						.indices().getIndex(getRequest).get();
				
				
				CreateIndexRequest createRequest = new CreateIndexRequest()
						.index(userIndex);
				
				createRequest.settings(getResponse.settings().get(kibanaIndex));
				
				Map<String, Object> configMapping = getResponse.mappings().get(kibanaIndex).get("config").getSourceAsMap();

				createRequest.mapping("config", configMapping);
				
				esClient.admin().indices().create(createRequest).actionGet();

				// Wait for health status of YELLOW
				ClusterHealthRequest healthRequest = new ClusterHealthRequest()
						.indices(new String[]{userIndex}).waitForYellowStatus();
				
				esClient.admin().cluster().health(healthRequest).actionGet().getStatus();
				
				return true;
			}
		} catch (ExecutionException | InterruptedException | IOException e) {
			logger.error("Unable to create initial Kibana index", e);
		}
		
		return false;
	}
	
	// this may return other than void later...
	private static void setDefaultIndex(String username, String project, Client esClient, String kibanaIndex, String kibanaVersion) {
		// this will create a default index of [index.]YYYY.MM.DD in .kibana.username
		String source = new DocumentBuilder()
				.defaultIndex(getIndexPattern(project))
				.build();
		
		executeUpdate(getKibanaIndex(username, kibanaIndex), DEFAULT_INDEX_TYPE, kibanaVersion, source, esClient);
	}
	
	private static void buildAdminAlias(String username, List<String> projects, Client esClient, String kibanaIndex, String kibanaVersion) {
		
		List<String> toAdd = new ArrayList<String>(projects);
		
		try {
			
			for ( String project : projects ) {
				// Check that the index exists before we try to alias it...
				IndicesExistsResponse existsResponse = esClient.admin().indices().prepareExists(getIndexPattern(project)).get();
				logger.debug("Checking if index {} with pattern '{}' exists? {}", project, getIndexPattern(project), existsResponse.isExists());
				if ( !existsResponse.isExists() || project.equalsIgnoreCase(ADMIN_ALIAS_NAME)) {
					toAdd.remove(project);
				}
			}
			
			if ( toAdd.isEmpty() )
				return;
			
			IndicesAliasesRequestBuilder aliasBuilder = esClient.admin().indices().prepareAliases();
			
			for ( String project : toAdd ) {
				logger.debug("Creating alias for {} as {}", project, ADMIN_ALIAS_NAME);
				aliasBuilder.addAlias(getIndexPattern(project), ADMIN_ALIAS_NAME);
			}
		
			IndicesAliasesResponse response = aliasBuilder.get();
			logger.debug("Aliases request acknowledged? {}", response.isAcknowledged());
		} catch (ElasticsearchException e) {
			// Avoid printing out any kibana specific information?
			logger.error("Error executing Alias request", e);
		}
		
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
			
			if ( e.getCause() instanceof RemoteTransportException && 
					e.getCause().getCause() instanceof IndexNotFoundException ) {
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

					if ( !project.equals(id) || project.equalsIgnoreCase(ADMIN_ALIAS_NAME) )
						patterns.add(project);

					// else -> this is user created, leave it alone
				}
				
		} catch (InterruptedException | ExecutionException e) {
			// if is ExecutionException with cause of IndexMissingException
			if ( e.getCause() instanceof RemoteTransportException &&
					e.getCause().getCause() instanceof IndexNotFoundException ) {
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
		
		if ( project.equalsIgnoreCase(OPERATIONS_PROJECT) || project.equalsIgnoreCase(ADMIN_ALIAS_NAME) )
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
	
	private static void executeUpdate(String index, String type, String id, String source, Client esClient) {
		
		logger.debug("UPDATE: '{}/{}/{}' source: '{}'", index, type, id, source);
		
		UpdateRequest request = esClient.prepareUpdate(index, type, id).setDoc(source).setDocAsUpsert(true).request();

		logger.debug("Created with update? '{}'", esClient.update(request).actionGet().isCreated());
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
		
		if ( project.equalsIgnoreCase(ADMIN_ALIAS_NAME) )
			return project;
		
		return project + ".*";
	}
	
	private static String getProjectFromIndex(String index) {
		
		if ( !StringUtils.isEmpty(index) ) {
			
			if ( index.equalsIgnoreCase(ADMIN_ALIAS_NAME) )
				return index;
			
			int wildcard = index.lastIndexOf('.');
			
			if ( wildcard > 0 )
				return index.substring(0, wildcard);
		}
			
		return index;
	}
}
