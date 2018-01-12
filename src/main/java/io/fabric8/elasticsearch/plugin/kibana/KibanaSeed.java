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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.support.ConfigConstants;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginClient;
import io.fabric8.elasticsearch.plugin.PluginSettings;
import io.fabric8.elasticsearch.util.IndexUtil;

public class KibanaSeed implements ConfigurationSettings {


    private static final String DEFAULT_INDEX_TYPE = "config";
    private static final String INDICIES_TYPE = "index-pattern";

    private static final String OPERATIONS_PROJECT = ".operations";
    private static final String BLANK_PROJECT = ".empty-project";
    private static final String ADMIN_ALIAS_NAME = ".all";
    private static final Logger LOGGER = Loggers.getLogger(KibanaSeed.class);

    public static final String DEFAULT_INDEX_FIELD = "defaultIndex";

    private final IndexMappingLoader mappingLoader;
    private final PluginClient pluginClient;
    private final String defaultKibanaIndex;
    private final PluginSettings settings;
    private final ThreadContext threadContext;

    public KibanaSeed(final PluginSettings settings, final IndexMappingLoader loader, final PluginClient pluginClient, final ThreadPool threadPool)  {
        this.mappingLoader = loader;
        this.pluginClient = pluginClient;
        this.defaultKibanaIndex = settings.getDefaultKibanaIndex();
        this.settings = settings;
        this.threadContext = threadPool.getThreadContext();
    }

    public void setDashboards(final OpenshiftRequestContext context, Client client, String kibanaVersion, final String projectPrefix) {

        LOGGER.debug("Begin setDashboards:  projectPrefix '{}' for user '{}' projects '{}' kibanaIndex '{}'",
                projectPrefix, context.getUser(), context.getProjects(), context.getKibanaIndex());

        // We want to seed the Kibana user index initially
        // since the logic from Kibana has changed to create before this plugin
        // starts...
        boolean changed = initialSeedKibanaIndex(context, client);

        // GET .../.kibana/index-pattern/_search?pretty=true&fields=
        // compare results to projects; handle any deltas (create, delete?)

        Set<String> indexPatterns = getProjectNamesFromIndexes(context, client, projectPrefix);
        LOGGER.debug("Found '{}' Index patterns for user", indexPatterns.size());

        Set<String> projects = new HashSet<>(context.getProjects());
        if(context.isOperationsUser()) {
            projects.add(OPERATIONS_PROJECT);
        }
        List<String> filteredProjects = new ArrayList<String>(filterProjectsWithIndices(projectPrefix, projects));
        LOGGER.debug("projects for '{}' that have existing indexes: '{}'", context.getUser(), filteredProjects);

        addAliasToAllProjects(context, filteredProjects);
        Collections.sort(filteredProjects);
        
        // If none have been set yet
        if (indexPatterns.isEmpty()) {
            create(context.getKibanaIndex(), filteredProjects, true, client, kibanaVersion, projectPrefix, indexPatterns);
            changed = true;
        } else {
            
            List<String> common = new ArrayList<String>(indexPatterns);

            common.retainAll(filteredProjects);

            filteredProjects.removeAll(common);
            indexPatterns.removeAll(common);

            // if we aren't a cluster-admin, make sure we're deleting the
            // ADMIN_ALIAS_NAME
            if (!context.isOperationsUser()) {
                LOGGER.debug("user is not a cluster admin, ensure they don't keep/have the admin alias pattern");
                indexPatterns.add(ADMIN_ALIAS_NAME);
            }

            // check if we're going to be adding or removing any projects
            if (!filteredProjects.isEmpty() || !indexPatterns.isEmpty()) {
                changed = true;
            }

            // for any to create (remaining in projects) call createIndices, createSearchmapping?, create dashboard
            create(context.getKibanaIndex(), filteredProjects, false, client, kibanaVersion, projectPrefix, indexPatterns);

            // cull any that are in ES but not in OS (remaining in indexPatterns)
            remove(context.getKibanaIndex(), indexPatterns, client, projectPrefix);

            common.addAll(filteredProjects);
            Collections.sort(common);
            // Set default index to first index in common if we removed the default
            String defaultIndex = getDefaultIndex(context, client, kibanaVersion, projectPrefix);

            LOGGER.debug("Checking if index patterns '{}' contain default index '{}'", indexPatterns, defaultIndex);

            if ( indexPatterns.contains(defaultIndex) || StringUtils.isEmpty(defaultIndex) ) {
                LOGGER.debug("'{}' does contain '{}' and common size is {}", indexPatterns, defaultIndex, common.size());
                if ( !common.isEmpty() ) {
                    setDefaultIndex(context.getKibanaIndex(), common.get(0), client, kibanaVersion, projectPrefix);
                }
            }
        }

        if ( changed ) {
            refreshKibanaUser(context.getKibanaIndex(), client);
        }
    }
    
    private void addAliasToAllProjects(final OpenshiftRequestContext context, List<String> filteredProjects) {
        String projectPrefix = settings.getCdmProjectPrefix();
        if (context.isOperationsUser()) {
            // Check roles here, if user is a cluster-admin we should add
            LOGGER.debug("Adding indexes to alias '{}' for user '{}'", ADMIN_ALIAS_NAME, context.getUser());
            buildAdminAlias(filteredProjects, projectPrefix);
            filteredProjects.add(ADMIN_ALIAS_NAME);
        } else if (filteredProjects.isEmpty()) {
            filteredProjects.add(BLANK_PROJECT);
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

    private void refreshKibanaUser(String kibanaIndex, Client esClient) {
        if(StringUtils.isBlank(threadContext.getTransient(ConfigConstants.SG_CHANNEL_TYPE))) {
            threadContext.putTransient(ConfigConstants.SG_CHANNEL_TYPE, "direct");
        }
        RefreshResponse response = esClient.admin().indices().prepareRefresh(kibanaIndex).get();
        LOGGER.debug("Refreshed '{}' successfully on {} of {} shards", kibanaIndex, response.getSuccessfulShards(),
                    response.getTotalShards());
    }

    private boolean initialSeedKibanaIndex(final OpenshiftRequestContext context, Client esClient) {
        try {
            String userIndex = context.getKibanaIndex();
            boolean kibanaIndexExists = pluginClient.indexExists(userIndex);
            LOGGER.debug("Kibana index '{}' exists? {}", userIndex, kibanaIndexExists);

            // copy the defaults if the userindex is not the kibanaindex
            if (!kibanaIndexExists && !defaultKibanaIndex.equals(userIndex)) {
                LOGGER.debug("Copying '{}' to '{}'", defaultKibanaIndex, userIndex);

                GetIndexResponse getResponse = pluginClient.getIndex(defaultKibanaIndex);
                Map<String, Object> configMapping = getResponse.mappings().get(defaultKibanaIndex).get("config")
                        .getSourceAsMap();
                if(StringUtils.isBlank(threadContext.getTransient(ConfigConstants.SG_CHANNEL_TYPE))) {
                    threadContext.putTransient(ConfigConstants.SG_CHANNEL_TYPE, "direct");
                }
                esClient.admin().indices().prepareCreate(userIndex)
                    .setSettings(Settings.builder()
                            .put("index.number_of_shards", 1)
                            .put("index.number_of_replicas", 0)
                            .build())
                    .addMapping("config", configMapping).get();


                // Wait for health status of YELLOW
                esClient.admin().cluster().prepareHealth(userIndex)
                    .setWaitForYellowStatus()
                    .get()
                    .getStatus();

                return true;
            }
        } catch (IOException e) {
            LOGGER.error("Unable to create initial Kibana index", e);
        }

        return false;
    }

    // this may return other than void later...
    private void setDefaultIndex(String kibanaIndex, String project, Client esClient,
            String kibanaVersion, String projectPrefix) {
        // this will create a default index of [index.]YYYY.MM.DD in
        // .kibana.username
        String source = new DocumentBuilder().defaultIndex(getIndexPattern(project, projectPrefix)).build();
        pluginClient.updateDocument(kibanaIndex, DEFAULT_INDEX_TYPE, kibanaVersion, source);

    }

    /*
     * Create admin alias for a list of projects which are known to one or more associated indexes
     */
    private void buildAdminAlias(List<String> projects, String projectPrefix) {
        buildUserAlias(projects, projectPrefix, ADMIN_ALIAS_NAME);
    }

    private void buildUserAlias(List<String> projects, String projectPrefix, String alias) {
        try {
            if (projects.isEmpty()) {
                return;
            }
            Set<String> aliasedIndicies = pluginClient.getIndicesForAlias(alias);
            aliasedIndicies = new IndexUtil().replaceDateSuffix("*", aliasedIndicies);
            Map<String, String> patternAlias = new HashMap<>(projects.size());
            for (String project : projects) {
                String indexPattern = getIndexPattern(project, projectPrefix);
                if(!aliasedIndicies.contains(indexPattern)) {
                    patternAlias.put(indexPattern, alias);
                }
            }
            pluginClient.alias(patternAlias);
            
        } catch (ElasticsearchException e) {
            // Avoid printing out any kibana specific information?
            LOGGER.error("Error executing Alias request", e);
        }
        
    }
    
    private String getDefaultIndex(OpenshiftRequestContext context, Client esClient, String kibanaVersion, String projectPrefix) {

        GetResponse response = pluginClient.getDocument(context.getKibanaIndex(), DEFAULT_INDEX_TYPE, kibanaVersion);
        if(response.isExists()) {
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
        } else {
            LOGGER.debug("Default index does not exist: '{}'", context.getKibanaIndex());
        }

        return "";
    }

    private void create(String kibanaIndex, List<String> projects, boolean setDefault, Client esClient,
            String kibanaVersion, String projectPrefix, Set<String> indexPatterns) {
        boolean defaultSet = !setDefault;
        LOGGER.trace("Creating index-patterns for projects: '{}'", projects);
        for (String project : projects) {
            if(indexPatterns.contains(project)) { //no need to update
                LOGGER.trace("Skipping creation of index-pattern for project '{}'. It already exists.", project);
                continue;
            }
            createIndexPattern(kibanaIndex, project, esClient, projectPrefix);

            // set default
            if (!defaultSet) {
                setDefaultIndex(kibanaIndex, project, esClient, kibanaVersion, projectPrefix);
                defaultSet = true;
            }
        }
    }
    

    private void remove(String kibanaIndex, Set<String> projects, Client esClient, String projectPrefix) {

        for (String project : projects) {
            pluginClient.deleteDocument(kibanaIndex, INDICIES_TYPE, getIndexPattern(project, projectPrefix));
        }
    }

    private Set<String> getProjectNamesFromIndexes(OpenshiftRequestContext context, Client esClient, String projectPrefix) {
        Set<String> patterns = new HashSet<String>();
        SearchResponse response = pluginClient.search(context.getKibanaIndex(), INDICIES_TYPE);
        if (response.getHits() != null && response.getHits().getTotalHits() > 0) {
            for (SearchHit hit : response.getHits().getHits()) {
                String id = hit.getId();
                String project = getProjectFromIndex(id, projectPrefix);

                if (!project.equals(id) || project.equalsIgnoreCase(ADMIN_ALIAS_NAME)) {
                    patterns.add(project);
                }

                // else -> this is user created, leave it alone
            }
        } else {
            LOGGER.debug("No index-mappings found in the kibana index '{}'", context.getKibanaIndex());
        }

        return patterns;
    }

    private void createIndexPattern(String kibanaIndex, String project, Client esClient, String projectPrefix) {

        final String indexPattern = getIndexPattern(project, projectPrefix);
        String source;
        if (project.equalsIgnoreCase(OPERATIONS_PROJECT) || project.startsWith(ADMIN_ALIAS_NAME)) {
            source = mappingLoader.getOperationsMappingsTemplate();
        } else if (project.equalsIgnoreCase(BLANK_PROJECT)) {
            source = mappingLoader.getEmptyProjectMappingsTemplate();
        } else {
            source = mappingLoader.getApplicationMappingsTemplate();
        }

        if (source != null) {
            LOGGER.trace("Creating index-pattern for project '{}'", project);
            source = source.replaceAll("$TITLE$", indexPattern);
            pluginClient.createDocument(kibanaIndex, INDICIES_TYPE, indexPattern, source);
        } else {
            LOGGER.debug("The source for the index mapping is null.  Skipping trying to create index pattern {}", indexPattern);
        }
    }

    private String getIndexPattern(String project, String projectPrefix) {

        if (project.startsWith(ADMIN_ALIAS_NAME)) {
            return project;
        }

        if (project.equalsIgnoreCase(OPERATIONS_PROJECT) || StringUtils.isEmpty(projectPrefix)) {
            return project + ".*";
        } else if (project.equalsIgnoreCase(BLANK_PROJECT)) { 
            return projectPrefix + project + ".*";
        } else {
            return projectPrefix + "." + project + ".*";
        }
    }

    private String getProjectFromIndex(String index, String projectPrefix) {

        if (!StringUtils.isEmpty(index)) {

            if (index.startsWith(ADMIN_ALIAS_NAME)) {
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
