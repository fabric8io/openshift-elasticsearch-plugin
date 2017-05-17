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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;

public class IndexMappingLoader implements ConfigurationSettings {
    
    public static final String DEFAULT_OPERATIONS_MAPPING = "io/fabric8/elasticsearch/plugin/kibana/operations_mapping.json";
    public static final String DEFAULT_APPLICATIONS_MAPPING = "io/fabric8/elasticsearch/plugin/kibana/applications_mapping.json";
    public static final String DEFAULT_EMPTY_MAPPING = "io/fabric8/elasticsearch/plugin/kibana/empty_project_mappings.json";

    private static ESLogger logger = Loggers.getLogger(IndexMappingLoader.class);
    private final String appMappingsTemplate;
    private final String opsMappingsTemplate;
    private final String emptyProjectMappingsTemplate;

    @Inject
    public IndexMappingLoader(final Settings settings) {
        appMappingsTemplate = loadMapping(settings, OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_APP,DEFAULT_APPLICATIONS_MAPPING);
        opsMappingsTemplate = loadMapping(settings, OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_OPERATIONS,DEFAULT_OPERATIONS_MAPPING);
        emptyProjectMappingsTemplate = loadMapping(settings, OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_EMPTY,DEFAULT_EMPTY_MAPPING);
    }
    
    private String loadMapping(final Settings settings, final String key, final String keyDefault) {
        String mapping = settings.get(key);
        if (mapping != null && new File(mapping).exists()) {
            logger.info("Trying to load Kibana mapping for {} from plugin: {}", key, mapping);
            try {
                InputStream stream = new FileInputStream(mapping.toString());
                return IOUtils.toString(stream);
            } catch (Exception e) {
                logger.error("Unable to load the Kibana mapping specified by {}: {}", key, e, mapping);
            }
        }
        logger.info("Loading default Kibana mapping for {} from plugin: {}", key, keyDefault);
        try {
            final ClassLoader classLoader = getClass().getClassLoader();
            return IOUtils.toString(classLoader.getResourceAsStream(keyDefault));
        }catch(Exception e) {
            logger.error("Unable to load the Kibana mapping specified by {}: {}", key, e, keyDefault);
        }
        return null;
    }

    public String getApplicationMappingsTemplate() {
        return appMappingsTemplate;
    }

    public String getOperationsMappingsTemplate() {
        return opsMappingsTemplate;
    }

    public String getEmptyProjectMappingsTemplate() {
        return emptyProjectMappingsTemplate;
    }
}
