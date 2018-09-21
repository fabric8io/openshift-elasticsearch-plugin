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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentFactory;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginClient;
import io.fabric8.elasticsearch.plugin.PluginSettings;
import io.fabric8.elasticsearch.plugin.model.Project;

public class KibanaSeed implements ConfigurationSettings {


    private static final String DEFAULT_INDEX_TYPE = "config";
    private static final String INDICIES_TYPE = "index-pattern";
    private static final Logger LOGGER = Loggers.getLogger(KibanaSeed.class);

    public static final String DEFAULT_INDEX_FIELD = "defaultIndex";

    private final IndexMappingLoader mappingLoader;
    private final PluginClient pluginClient;
    private final String defaultKibanaIndex;
    private final PluginSettings settings;
    private final KibanaUtils kibanaUtils;

    public KibanaSeed(final PluginSettings settings, final IndexMappingLoader loader, final PluginClient pluginClient, final KibanaUtils kibanaUtils)  {
        this.mappingLoader = loader;
        this.pluginClient = pluginClient;
        this.defaultKibanaIndex = settings.getDefaultKibanaIndex();
        this.settings = settings;
        this.kibanaUtils = kibanaUtils;
    }
    
    public void setDashboards(final OpenshiftRequestContext context, String kibanaVersion, final String projectPrefix) {
        if(!pluginClient.indexExists(defaultKibanaIndex)) {
            LOGGER.debug("Default Kibana index '{}' does not exist. Skipping Kibana seeding", defaultKibanaIndex);
            return;
        }

        LOGGER.debug("Begin setDashboards:  projectPrefix '{}' for user '{}' projects '{}' kibanaIndex '{}'",
                projectPrefix, context.getUser(), context.getProjects(), context.getKibanaIndex());
        
        // We want to seed the Kibana user index initially
        // since the logic from Kibana has changed to create before this plugin
        // starts...
        boolean changed = initialSeedKibanaIndex(context);
        
        if (context.isOperationsUser()) {
            changed = seedOperationsIndexPatterns(context, kibanaVersion, projectPrefix);
        } else {
            changed = seedUsersIndexPatterns(context, kibanaVersion, projectPrefix);
        }

        if ( changed ) {
            pluginClient.refreshIndices(context.getKibanaIndex());
        }
    }

    private boolean seedOperationsIndexPatterns(final OpenshiftRequestContext context, String kibanaVersion, final String projectPrefix) {
        boolean changed = false;
        boolean defaultSet = false;
        for (String pattern : settings.getKibanaOpsIndexPatterns()) {
            if(!pluginClient.documentExists(context.getKibanaIndex(), INDICIES_TYPE, pattern)) {
                LOGGER.trace("Creating index-pattern '{}'", pattern);
                String source = StringUtils.replace(mappingLoader.getOperationsMappingsTemplate(), "$TITLE$", pattern);
                pluginClient.createDocument(context.getKibanaIndex(), INDICIES_TYPE, pattern, source);
                if (!defaultSet) {
                    try {
                        String update = XContentFactory.jsonBuilder()
                                .startObject()
                                    .field(KibanaSeed.DEFAULT_INDEX_FIELD, pattern)
                                 .endObject().string();
                        pluginClient.update(context.getKibanaIndex(), DEFAULT_INDEX_TYPE, kibanaVersion, update);
                        defaultSet = true;
                    } catch (IOException e) {
                        LOGGER.error("Unable to set default index-pattern", e);
                    }
                }
                changed = true;
            }
        }
        return changed;
    }
    
    private boolean seedUsersIndexPatterns(final OpenshiftRequestContext context,  final String kibanaVersion, final String projectPrefix) {
        boolean changed = false;
        // GET .../.kibana/index-pattern/_search?pretty=true&fields=
        // compare results to projects; handle any deltas (create, delete?)
        Set<Project> projectsFromIndexPatterns = kibanaUtils.getProjectsFromIndexPatterns(context);
        LOGGER.debug("Found '{}' Index patterns for user", projectsFromIndexPatterns.size());

        Set<Project> projects = context.getProjects();
        List<Project> projectsWithIndices = filterProjectsWithIndices(projectPrefix, projects);
        LOGGER.debug("projects for '{}' that have existing index patterns: '{}'", context.getUser(), projectsWithIndices);
        
        if (projectsWithIndices.isEmpty()) {
            projectsWithIndices.add(KibanaUtils.EMPTY_PROJECT);
        }
        
        Collections.sort(projectsWithIndices);
        
        // If none have been set yet
        if (projectsFromIndexPatterns.isEmpty()) {
            create(context.getKibanaIndex(), projectsWithIndices, true, kibanaVersion, projectPrefix, projectsFromIndexPatterns);
            changed = true;
        } else {
            
            List<Project> common = new ArrayList<Project>(projectsFromIndexPatterns);

            common.retainAll(projectsWithIndices);

            projectsWithIndices.removeAll(common);
            projectsFromIndexPatterns.removeAll(common);

            // if we aren't a cluster-admin, make sure we're deleting the
            // ADMIN_ALIAS_NAME
            if (!context.isOperationsUser()) {
                LOGGER.debug("user is not a cluster admin, ensure they don't keep/have the admin alias pattern");
                projectsFromIndexPatterns.add(KibanaUtils.ALL_ALIAS);
            }

            // check if we're going to be adding or removing any projects
            if (!projectsWithIndices.isEmpty() || !projectsFromIndexPatterns.isEmpty()) {
                changed = true;
            }

            // for any to create (remaining in projects) call createIndices, createSearchmapping?, create dashboard
            create(context.getKibanaIndex(), projectsWithIndices, false, kibanaVersion, projectPrefix, projectsFromIndexPatterns);

            // cull any that are in ES but not in OS (remaining in indexPatterns)
            remove(context.getKibanaIndex(), projectsFromIndexPatterns);

            common.addAll(projectsWithIndices);
            Collections.sort(common);
            // Set default index to first index in common if we removed the default
            Project defaultProject = getDefaultProject(context, kibanaVersion);

            LOGGER.debug("Checking if index patterns '{}' contain default index '{}'", projectsFromIndexPatterns, defaultProject);

            if ( projectsFromIndexPatterns.contains(defaultProject) || Project.EMPTY.equals(defaultProject) ) {
                LOGGER.debug("'{}' does contain '{}' and common size is {}", projectsFromIndexPatterns, defaultProject, common.size());
                if ( !common.isEmpty() ) {
                    setDefaultProject(context.getKibanaIndex(), common.get(0), kibanaVersion);
                }
            }
        }
        return changed;
    }

    /*
     * Given a list of projects, filter out those which have an
     * index associated with it
     */
    private List<Project> filterProjectsWithIndices(final String projectPrefix, Set<Project> projects){
        List<Project> result = new ArrayList<>(projects.size());
        for (Project project : projects) {
            String indexPattern = kibanaUtils.formatIndexPattern(project);
            if (pluginClient.indexExists(indexPattern)) {
                result.add(project);
            }
        }
        return result;
    }

    private boolean initialSeedKibanaIndex(final OpenshiftRequestContext context) {
        try {
            String userIndex = context.getKibanaIndex();
            boolean kibanaIndexExists = pluginClient.indexExists(userIndex);
            LOGGER.debug("Kibana index '{}' exists? {}", userIndex, kibanaIndexExists);

            // copy the defaults if the userindex is not the kibanaindex
            if (!kibanaIndexExists && !defaultKibanaIndex.equals(userIndex)) {
                LOGGER.debug("Copying '{}' to '{}'", defaultKibanaIndex, userIndex);
                Settings settings = Settings.builder()
                        .put("index.number_of_shards", 1)
                        .put("index.number_of_replicas", 0)
                        .build();
                pluginClient.copyIndex(defaultKibanaIndex, userIndex, settings, DEFAULT_INDEX_TYPE);
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Unable to create initial Kibana index", e);
        }

        return false;
    }

    private void setDefaultProject(String kibanaIndex, Project project, String kibanaVersion) {
        // this will create a default index-pattern of in .kibana.USERNAMEHASH
        String source = new DocumentBuilder().defaultIndex(kibanaUtils.formatIndexPattern(project)).build();
        pluginClient.updateDocument(kibanaIndex, DEFAULT_INDEX_TYPE, kibanaVersion, source);

        pluginClient.update(kibanaIndex, DEFAULT_INDEX_TYPE, kibanaVersion, source);
    }

    private Project getDefaultProject(OpenshiftRequestContext context, String kibanaVersion) {

        GetResponse response = pluginClient.getDocument(context.getKibanaIndex(), DEFAULT_INDEX_TYPE, kibanaVersion);
        if(response.isExists()) {
            Map<String, Object> source = response.getSource();
            // if source == null then its a different version of kibana that was
            // used -- we'll need to recreate
            if (source != null && source.containsKey(DEFAULT_INDEX_FIELD)) {
                LOGGER.debug("Received response with 'defaultIndex' = {}", source.get(DEFAULT_INDEX_FIELD));
                String index = (String) source.get(DEFAULT_INDEX_FIELD);
                
                return kibanaUtils.getProjectFromIndexPattern(index);
            } else {
                LOGGER.debug("Received response without 'defaultIndex'");
            }
        } else {
            LOGGER.debug("Default index does not exist: '{}'", context.getKibanaIndex());
        }

        return Project.EMPTY;
    }

    private void create(String kibanaIndex, List<Project> projects, boolean setDefault,
            String kibanaVersion, String projectPrefix, Set<Project> projectsWithIndexPatterns) {
        boolean defaultSet = !setDefault;
        LOGGER.trace("Creating index-patterns for projects: '{}'", projects);
        for (Project project : projects) {
            if(projectsWithIndexPatterns.contains(project)) { //no need to update
                LOGGER.trace("Skipping creation of index-pattern for project '{}'. It already exists.", project);
                continue;
            }
            createIndexPattern(kibanaIndex, project, projectPrefix);

            // set default
            if (!defaultSet) {
                setDefaultProject(kibanaIndex, project, kibanaVersion);
                defaultSet = true;
            }
        }
    }
    

    private void remove(String kibanaIndex, Set<Project> projects) {

        for (Project project : projects) {
            pluginClient.deleteDocument(kibanaIndex, INDICIES_TYPE, kibanaUtils.formatIndexPattern(project));
        }
    }

    private void createIndexPattern(String kibanaIndex, Project project, String projectPrefix) {

        final String indexPattern = kibanaUtils.formatIndexPattern(project);
        String source;
        if (project.equals(KibanaUtils.EMPTY_PROJECT)) {
            source = mappingLoader.getEmptyProjectMappingsTemplate();
        } else {
            source = mappingLoader.getApplicationMappingsTemplate();
        }

        if (source != null) {
            LOGGER.trace("Creating index-pattern for project '{}'", project);
            source = source.replaceAll("\\$TITLE\\$", indexPattern);
            pluginClient.createDocument(kibanaIndex, INDICIES_TYPE, indexPattern, source);
        } else {
            LOGGER.debug("The source for the index mapping is null.  Skipping trying to create index pattern {}", indexPattern);
        }
    }

}
