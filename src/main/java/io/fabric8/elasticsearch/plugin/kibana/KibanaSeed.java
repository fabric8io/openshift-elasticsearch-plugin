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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginClient;
import io.fabric8.elasticsearch.plugin.PluginSettings;
import io.fabric8.elasticsearch.plugin.model.Project;

public class KibanaSeed implements ConfigurationSettings {

    private static final String CONFIG_DOC_TYPE = "config";
    private static final String INDICIES_TYPE = "index-pattern";
    private static final Logger LOGGER = Loggers.getLogger(KibanaSeed.class);

    public static final String DEFAULT_INDEX_FIELD = "defaultIndex";

    private final IndexMappingLoader mappingLoader;
    private final PluginClient pluginClient;
    private final String defaultKibanaIndex;
    private final PluginSettings settings;
    private final KibanaUtils kibanaUtils;

    public KibanaSeed(final PluginSettings settings, final IndexMappingLoader loader, final PluginClient pluginClient,
            final KibanaUtils kibanaUtils) {
        this.mappingLoader = loader;
        this.pluginClient = pluginClient;
        this.defaultKibanaIndex = settings.getDefaultKibanaIndex();
        this.settings = settings;
        this.kibanaUtils = kibanaUtils;
    }

    public void setDashboards(final OpenshiftRequestContext context, String kibanaVersion, final String projectPrefix) {
        if (!pluginClient.indexExists(defaultKibanaIndex)) {
            LOGGER.debug("Default Kibana index '{}' does not exist. Skipping Kibana seeding", defaultKibanaIndex);
            return;
        }

        LOGGER.debug("Begin setDashboards:  projectPrefix '{}' for user '{}' projects '{}' kibanaIndex '{}'",
                projectPrefix, context.getUser(), context.getProjects(), context.getKibanaIndex());

        // We want to seed the Kibana user index initially
        // since the logic from Kibana has changed to create before this plugin
        // starts...
        Tuple<Boolean, Project> action = Tuple.tuple(initialSeedKibanaIndex(context), Project.EMPTY);

        if (context.isOperationsUser()) {
            action = seedOperationsIndexPatterns(context, kibanaVersion);
        } else {
            action = seedUsersIndexPatterns(context, kibanaVersion);
        }

        if (action.v2() != null && !Project.EMPTY.equals(action.v2())) {
            boolean defaultIndexPatternExists = pluginClient.documentExists(context.getKibanaIndex(), INDICIES_TYPE, action.v2().getName());
            boolean kibanaConfigExists = pluginClient.documentExists(context.getKibanaIndex(), CONFIG_DOC_TYPE, kibanaVersion);
            if(!defaultIndexPatternExists || !kibanaConfigExists){
                setDefaultProject(context.getKibanaIndex(), action.v2(), kibanaVersion);
            }
        }

        if (action.v1()) {
            pluginClient.refreshIndices(context.getKibanaIndex());
        }
    }

    /*
     * @return The indicator that a change was made and the default index-pattern to
     * set
     */
    private Tuple<Boolean, Project> seedOperationsIndexPatterns(final OpenshiftRequestContext context, String kibanaVersion) {
        boolean changed = false;
        for (String pattern : settings.getKibanaOpsIndexPatterns()) {
            if (!pluginClient.documentExists(context.getKibanaIndex(), INDICIES_TYPE, pattern)) {
                LOGGER.trace("Creating index-pattern '{}'", pattern);
                String source = StringUtils.replace(mappingLoader.getOperationsMappingsTemplate(), "$TITLE$", pattern);
                pluginClient.createDocument(context.getKibanaIndex(), INDICIES_TYPE, pattern, source);
                changed = true;
            }
        }
        // if current.default not set, load
        String defaultPattern = settings.getKibanaOpsIndexPatterns().size() > 0 
                ? settings.getKibanaOpsIndexPatterns().iterator().next() : "";
        String indexPattern = kibanaUtils.getDefaultIndexPattern(context.getKibanaIndex(), defaultPattern);
        return Tuple.tuple(changed, new Project(indexPattern, null));
    }

    private Tuple<Boolean, Project> seedUsersIndexPatterns(final OpenshiftRequestContext context, final String kibanaVersion) {
        boolean changed = false;
        Project defaultProject = Project.EMPTY;
        Set<Project> projectsFromIndexPatterns = kibanaUtils.getProjectsFromIndexPatterns(context);
        LOGGER.debug("Found '{}' Index patterns for user", projectsFromIndexPatterns.size());

        Set<Project> projects = context.getProjects();
        List<Project> projectsWithIndices = filterProjectsWithIndices(projects);
        LOGGER.debug("projects for '{}' that have existing index patterns: '{}'", context.getUser(),
                projectsWithIndices);

        if (projectsWithIndices.isEmpty()) {
            projectsWithIndices.add(KibanaUtils.EMPTY_PROJECT);
        }

        Collections.sort(projectsWithIndices);

        // If none have been set yet
        if (projectsFromIndexPatterns.isEmpty()) {
            create(context.getKibanaIndex(), projectsWithIndices, projectsFromIndexPatterns);
            changed = true;
            defaultProject = projectsWithIndices.isEmpty() ? null : projectsWithIndices.get(0);
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

            // check if we're going to be adding or removing any index-patterns
            if (!projectsWithIndices.isEmpty() || !projectsFromIndexPatterns.isEmpty()) {
                changed = true;
            }

            // for any to create (remaining in projects) call createIndices,
            // createSearchmapping?, create dashboard
            create(context.getKibanaIndex(), projectsWithIndices, projectsFromIndexPatterns);

            // cull any that are in ES but not in OS (remaining in indexPatterns)
            remove(context.getKibanaIndex(), projectsFromIndexPatterns);

            common.addAll(projectsWithIndices);
            Collections.sort(common);
            // Set default index to first index in common if we removed the default
            String defaultIfNotSet = !common.isEmpty() ? common.get(0).getName() : Project.EMPTY.getName();
            String pattern = kibanaUtils.getDefaultIndexPattern(context.getKibanaIndex(), defaultIfNotSet);
            defaultProject = new Project(pattern, null);
        }
        return Tuple.tuple(changed, defaultProject);
    }

    /*
     * Given a list of projects, filter out those which have an index associated
     * with it
     */
    private List<Project> filterProjectsWithIndices(Set<Project> projects) {
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
                pluginClient.copyIndex(defaultKibanaIndex, userIndex, settings, CONFIG_DOC_TYPE);
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
        pluginClient.updateDocument(kibanaIndex, CONFIG_DOC_TYPE, kibanaVersion, source);
    }

    private void create(String kibanaIndex, List<Project> projects, Set<Project> projectsWithIndexPatterns) {
        LOGGER.trace("Creating index-patterns for projects: '{}'", projects);
        for (Project project : projects) {
            if (projectsWithIndexPatterns.contains(project)) { // no need to update
                LOGGER.trace("Skipping creation of index-pattern for project '{}'. It already exists.", project);
                continue;
            }
            createIndexPattern(kibanaIndex, project, settings.getCdmProjectPrefix());
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
            LOGGER.debug("The source for the index mapping is null.  Skipping trying to create index pattern {}",
                    indexPattern);
        }
    }

}
