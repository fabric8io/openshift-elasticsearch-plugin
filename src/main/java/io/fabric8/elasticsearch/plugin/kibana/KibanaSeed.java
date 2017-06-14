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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
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
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.transport.RemoteTransportException;

import com.floragunn.searchguard.support.ConfigConstants;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.PluginClient;

public class KibanaSeed implements ConfigurationSettings {


    private static final String DEFAULT_INDEX_TYPE = "config";
    private static final String INDICIES_TYPE = "index-pattern";

    private static final String OPERATIONS_PROJECT = ".operations";
    private static final String BLANK_PROJECT = ".empty-project";
    private static final String ADMIN_ALIAS_NAME = ".all";
    private static final ESLogger LOGGER = Loggers.getLogger(KibanaSeed.class);

    // TODO: should these be able to be read from property values?
    private static final String[] OPERATIONS_ROLES = { "operations-user" };

    public static final String DEFAULT_INDEX_FIELD = "defaultIndex";

    private final IndexMappingLoader mappingLoader;
    private final PluginClient pluginClient;

    @Inject
    public KibanaSeed(IndexMappingLoader loader, PluginClient pluginClient)  {
        this.mappingLoader = loader;
        this.pluginClient = pluginClient;
    }

    public void setDashboards(String user, Set<String> projects, Set<String> roles, Client client, String kibanaIndex, 
            String kibanaVersion, final String projectPrefix) {

        LOGGER.debug("Begin setDashboards:  projectPrefix '{}' for user '{}' projects '{}' kibanaIndex '{}'",
                projectPrefix, user, projects, kibanaIndex);

        // We want to seed the Kibana user index initially
        // since the logic from Kibana has changed to create before this plugin
        // starts...
        AtomicBoolean changed = new AtomicBoolean(initialSeedKibanaIndex(user, kibanaIndex, client));

        boolean isAdmin = false;
        // GET .../.kibana/index-pattern/_search?pretty=true&fields=
        // compare results to projects; handle any deltas (create, delete?)

        Set<String> indexPatterns = getIndexPatterns(user, client, kibanaIndex, projectPrefix);
        LOGGER.debug("Found '{}' Index patterns for user", indexPatterns.size());

        // Check roles here, if user is a cluster-admin we should add
        // .operations to their project? -- correct way to do this?
        LOGGER.debug("Checking for '{}' in users roles '{}'", OPERATIONS_ROLES, roles);
        for (String role : OPERATIONS_ROLES) {
            if (roles.contains(role)) {
                LOGGER.debug("{} is an operations user", user);
                projects.add(OPERATIONS_PROJECT);
                isAdmin = true;
                projects.add(ADMIN_ALIAS_NAME);
                break;
            }
        }

        List<String> filteredProjects = new ArrayList<String>(filterProjectsWithIndices(projectPrefix, projects));
        Collections.sort(filteredProjects);
        LOGGER.debug("projects for '{}' that have existing indexes: '{}'", user, filteredProjects);

        if (isAdmin) {
            LOGGER.debug("Adding indexes to alias '{}' for user '{}'", ADMIN_ALIAS_NAME, user);
            buildAdminAlias(filteredProjects, projectPrefix);
        }

        if (filteredProjects.isEmpty()) {
            filteredProjects.add(BLANK_PROJECT);
        }
        
        // If none have been set yet
        if (indexPatterns.isEmpty()) {
            create(user, filteredProjects, true, client, kibanaIndex, kibanaVersion, projectPrefix);
            changed.set(true);
        } else {
            List<String> common = new ArrayList<String>(indexPatterns);

            common.retainAll(filteredProjects);

            filteredProjects.removeAll(common);
            indexPatterns.removeAll(common);

            // if we aren't a cluster-admin, make sure we're deleting the
            // ADMIN_ALIAS_NAME
            if (!isAdmin) {
                LOGGER.debug("user is not a cluster admin, ensure they don't keep/have the admin alias pattern");
                indexPatterns.add(ADMIN_ALIAS_NAME);
            }

            // check if we're going to be adding or removing any projects
            if (filteredProjects.size() > 0 || indexPatterns.size() > 0) {
                changed.set(true);
            }

            // for any to create (remaining in projects) call createIndices, createSearchmapping?, create dashboard
            create(user, filteredProjects, false, client, kibanaIndex, kibanaVersion, projectPrefix);

            // cull any that are in ES but not in OS (remaining in indexPatterns)
            remove(user, indexPatterns, client, kibanaIndex, projectPrefix);

            common.addAll(filteredProjects);
            Collections.sort(common);
            // Set default index to first index in common if we removed the default
            String defaultIndex = getDefaultIndex(user, client, kibanaIndex, kibanaVersion, projectPrefix);

            LOGGER.debug("Checking if index patterns '{}' contain default index '{}'", indexPatterns, defaultIndex);

            if ( indexPatterns.contains(defaultIndex) || StringUtils.isEmpty(defaultIndex) ) {
                LOGGER.debug("'{}' does contain '{}' and common size is {}", indexPatterns, defaultIndex, common.size());
                if ( !common.isEmpty() ) {
                    setDefaultIndex(user, common.get(0), client, kibanaIndex, kibanaVersion, projectPrefix);
                }
            }
        }

        if ( changed.get() ) {
            refreshKibanaUser(user, kibanaIndex, client);
        }
    }
    
    /*
     * Given a list of projects, filter out those which do not have any
     * index associated with it
     */
    private List<String> filterProjectsWithIndices(final String projectPrefix, Set<String> projects){
        List<String> result = new ArrayList<>(projects.size());
        for (String project : projects) {
            String index = getIndexPattern(project, projectPrefix);
            if (pluginClient.indexExists(index)) {
                result.add(project);
            }
        }
        return result;
    }

    private void refreshKibanaUser(String username, String kibanaIndex, Client esClient) {

        String userIndex = getKibanaIndex(username, kibanaIndex);
        RefreshRequest request = new RefreshRequest().indices(userIndex);
        request.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
        RefreshResponse response = esClient.admin().indices().refresh(request).actionGet();

        LOGGER.debug("Refreshed '{}' successfully on {} of {} shards", userIndex, response.getSuccessfulShards(),
                response.getTotalShards());
    }

    private boolean initialSeedKibanaIndex(String username, String kibanaIndex, Client esClient) {

        try {
            String userIndex = getKibanaIndex(username, kibanaIndex);
            boolean kibanaIndexExists = pluginClient.indexExists(userIndex);
            LOGGER.debug("Kibana index '{}' exists? {}", userIndex, kibanaIndexExists);

            if (!kibanaIndexExists) {
                LOGGER.debug("Copying '{}' to '{}'", kibanaIndex, userIndex);

                GetIndexRequest getRequest = new GetIndexRequest().indices(kibanaIndex);
                getRequest.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
                GetIndexResponse getResponse = esClient.admin().indices().getIndex(getRequest).get();

                CreateIndexRequest createRequest = new CreateIndexRequest().index(userIndex);
                createRequest.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");

                createRequest.settings(getResponse.settings().get(kibanaIndex));

                Map<String, Object> configMapping = getResponse.mappings().get(kibanaIndex).get("config")
                        .getSourceAsMap();

                createRequest.mapping("config", configMapping);

                esClient.admin().indices().create(createRequest).actionGet();

                // Wait for health status of YELLOW
                ClusterHealthRequest healthRequest = new ClusterHealthRequest().indices(new String[] { userIndex })
                        .waitForYellowStatus();

                esClient.admin().cluster().health(healthRequest).actionGet().getStatus();

                return true;
            }
        } catch (ExecutionException | InterruptedException | IOException e) {
            LOGGER.error("Unable to create initial Kibana index", e);
        }

        return false;
    }

    // this may return other than void later...
    private void setDefaultIndex(String username, String project, Client esClient, String kibanaIndex,
            String kibanaVersion, String projectPrefix) {
        // this will create a default index of [index.]YYYY.MM.DD in
        // .kibana.username
        String source = new DocumentBuilder().defaultIndex(getIndexPattern(project, projectPrefix)).build();

        executeUpdate(getKibanaIndex(username, kibanaIndex), DEFAULT_INDEX_TYPE, kibanaVersion, source, esClient);
    }

    /*
     * Create admin alias for a list of projects which are known to one or more associated indexes
     */
    private void buildAdminAlias(List<String> projects, String projectPrefix) {
        try {
            if (projects.isEmpty()) {
                return;
            }
            Map<String, String> patternAlias = new HashMap<>(projects.size());
            for (String project : projects) {
                patternAlias.put(getIndexPattern(project, projectPrefix), ADMIN_ALIAS_NAME);
            }
            pluginClient.alias(patternAlias);

        } catch (ElasticsearchException e) {
            // Avoid printing out any kibana specific information?
            LOGGER.error("Error executing Alias request", e);
        }

    }

    private String getDefaultIndex(String username, Client esClient, String kibanaIndex, String kibanaVersion,
            String projectPrefix) {
        GetRequest request = esClient
                .prepareGet(getKibanaIndex(username, kibanaIndex), DEFAULT_INDEX_TYPE, kibanaVersion)
                .putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true")
                .request();

        try {
            GetResponse response = esClient.get(request).get();

            Map<String, Object> source = response.getSource();

            // if source == null then its a different version of kibana that was
            // used -- we'll need to recreate
            if (source != null && source.containsKey(DEFAULT_INDEX_FIELD)) {
                LOGGER.debug("Received response with 'defaultIndex' = {}", source.get(DEFAULT_INDEX_FIELD));
                String index = (String) source.get(DEFAULT_INDEX_FIELD);

                return getProjectFromIndex(index, projectPrefix);
            } else {
                LOGGER.debug("Received response without 'defaultIndex'");
            }

        } catch (InterruptedException | ExecutionException e) {

            if (e.getCause() instanceof RemoteTransportException
                    && e.getCause().getCause() instanceof IndexNotFoundException) {
                LOGGER.debug("No index found");
            } else {
                LOGGER.error("Error getting default index for {}", e, username);
            }
        }

        return "";
    }

    private void create(String user, List<String> projects, boolean setDefault, Client esClient, String kibanaIndex,
            String kibanaVersion, String projectPrefix) {
        boolean defaultSet = !setDefault;

        for (String project : projects) {
            createIndex(user, project, esClient, kibanaIndex, projectPrefix);

            // set default
            if (!defaultSet) {
                setDefaultIndex(user, project, esClient, kibanaIndex, kibanaVersion, projectPrefix);
                defaultSet = true;
            }
        }
    }
    

    private void remove(String user, Set<String> projects, Client esClient, String kibanaIndex,
            String projectPrefix) {

        for (String project : projects) {
            deleteIndex(user, project, esClient, kibanaIndex, projectPrefix);
        }
    }

    // This is a mis-nomer... it actually returns the project name of index
    // patterns (.operations included)
    private Set<String> getIndexPatterns(String username, Client esClient, String kibanaIndex,
            String projectPrefix) {

        Set<String> patterns = new HashSet<String>();

        SearchRequest request = esClient.prepareSearch(getKibanaIndex(username, kibanaIndex)).setTypes(INDICIES_TYPE)
                .putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true")
                .request();

        try {
            SearchResponse response = esClient.search(request).get();

            if (response.getHits() != null && response.getHits().getTotalHits() > 0) {
                for (SearchHit hit : response.getHits().getHits()) {
                    String id = hit.getId();
                    String project = getProjectFromIndex(id, projectPrefix);

                    if (!project.equals(id) || project.equalsIgnoreCase(ADMIN_ALIAS_NAME)) {
                        patterns.add(project);
                    }

                    // else -> this is user created, leave it alone
                }
            }

        } catch (InterruptedException | ExecutionException e) {
            // if is ExecutionException with cause of IndexMissingException
            if (e.getCause() instanceof RemoteTransportException
                    && e.getCause().getCause() instanceof IndexNotFoundException) {
                LOGGER.debug("Encountered IndexMissingException, returning empty response");
            } else {
                LOGGER.error("Error getting index patterns for {}", e, username);
            }
        }

        return patterns;
    }

    private void createIndex(String username, String project, Client esClient, String kibanaIndex,
            String projectPrefix) {

        final String indexPattern = getIndexPattern(project, projectPrefix);
        String source;
        if (project.equalsIgnoreCase(OPERATIONS_PROJECT) || project.equalsIgnoreCase(ADMIN_ALIAS_NAME)) {
            source = mappingLoader.getOperationsMappingsTemplate();
        } else if (project.equalsIgnoreCase(BLANK_PROJECT)) {
            source = mappingLoader.getEmptyProjectMappingsTemplate();
        } else {
            source = mappingLoader.getApplicationMappingsTemplate();
        }

        if (source != null) {
            source = source.replaceAll("$TITLE$", indexPattern);
            executeCreate(getKibanaIndex(username, kibanaIndex), INDICIES_TYPE, indexPattern, source, esClient);
        } else {
            LOGGER.debug("The source for the index mapping is null.  Skipping trying to createIndex {}", indexPattern);
        }
    }

    private void deleteIndex(String username, String project, Client esClient, String kibanaIndex,
            String projectPrefix) {

        executeDelete(getKibanaIndex(username, kibanaIndex), INDICIES_TYPE, getIndexPattern(project, projectPrefix),
                esClient);
    }

    private void executeCreate(String index, String type, String id, String source, Client esClient) {

        LOGGER.debug("CREATE: '{}/{}/{}' source: '{}'", index, type, id, source);

        IndexRequest request = esClient.prepareIndex(index, type, id).setSource(source).request();
        request.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");

        try {
            esClient.index(request).get();
        } catch (InterruptedException | ExecutionException e) {
            // Avoid printing out any kibana specific information?
            LOGGER.error("Error executing create request", e);
        }
    }

    private void executeUpdate(String index, String type, String id, String source, Client esClient) {

        LOGGER.debug("UPDATE: '{}/{}/{}' source: '{}'", index, type, id, source);

        UpdateRequest request = esClient.prepareUpdate(index, type, id).setDoc(source).setDocAsUpsert(true).request();
        request.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");


        LOGGER.debug("Created with update? '{}'", esClient.update(request).actionGet().isCreated());
    }

    private void executeDelete(String index, String type, String id, Client esClient) {

        LOGGER.debug("DELETE: '{}/{}/{}'", index, type, id);

        DeleteRequest request = esClient.prepareDelete(index, type, id).request();
        request.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
        try {
            esClient.delete(request).get();
        } catch (InterruptedException | ExecutionException e) {
            // Avoid printing out any kibana specific information?
            LOGGER.error("Error executing delete request", e);
        }
    }

    private String getKibanaIndex(String username, String kibanaIndex) {
        return kibanaIndex + "." + getUsernameHash(username);
    }

    private String getIndexPattern(String project, String projectPrefix) {

        if (project.equalsIgnoreCase(ADMIN_ALIAS_NAME)) {
            return project;
        }

        if (project.equalsIgnoreCase(OPERATIONS_PROJECT) || StringUtils.isEmpty(projectPrefix)) {
            return project + ".*";
        } else {
            return projectPrefix + "." + project + ".*";
        }
    }

    private String getProjectFromIndex(String index, String projectPrefix) {

        if (!StringUtils.isEmpty(index)) {

            if (index.equalsIgnoreCase(ADMIN_ALIAS_NAME)) {
                return index;
            }

            int wildcard = index.lastIndexOf('.');

            if (wildcard > 0) {
                int start = 0;
                String projectPrefixTest = projectPrefix;
                if (StringUtils.isNotEmpty(projectPrefix)) {
                    projectPrefixTest = projectPrefix + ".";
                }
                if (index.startsWith(projectPrefixTest)) {
                    start = projectPrefixTest.length();
                }
                if (wildcard > start) {
                    return index.substring(start, wildcard);
                }
            }
        }

        return index;
    }
}
