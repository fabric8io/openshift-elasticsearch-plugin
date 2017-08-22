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

import static io.fabric8.elasticsearch.plugin.acl.SearchGuardSyncStrategyFactory.PROJECT;
import static io.fabric8.elasticsearch.plugin.acl.SearchGuardSyncStrategyFactory.USER;

import org.apache.commons.lang.ArrayUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;

public class PluginSettings implements ConfigurationSettings {

    private static final Logger LOGGER = Loggers.getLogger(PluginSettings.class);

    private String kibanaIndexMode;
    private String roleStrategy;
    private final String cdmProjectPrefix;
    private final String defaultKibanaIndex;
    private final String searchGuardIndex;
    private final String kibanaVersion;
    private final String kbnVersionHeader;
    private final Boolean enabled;
    private final Boolean reWriteEnabled;
    private final Settings settings;

    public PluginSettings(final Settings settings) {
        this.settings = settings;
        this.kibanaIndexMode = settings.get(OPENSHIFT_KIBANA_INDEX_MODE, KibanaIndexMode.DEFAULT_MODE);
        if (!ArrayUtils.contains(new String[] { UNIQUE, SHARED_OPS, SHARED_NON_OPS }, kibanaIndexMode.toLowerCase())) {
            this.kibanaIndexMode = UNIQUE;
        }

        this.roleStrategy = settings.get(OPENSHIFT_ACL_ROLE_STRATEGY, DEFAULT_ACL_ROLE_STRATEGY);
        if (!ArrayUtils.contains(new String[] { PROJECT, USER }, roleStrategy.toLowerCase())) {
            this.kibanaIndexMode = USER;
        }

        this.cdmProjectPrefix = settings.get(OPENSHIFT_CONFIG_PROJECT_INDEX_PREFIX,
                OPENSHIFT_DEFAULT_PROJECT_INDEX_PREFIX);
        this.defaultKibanaIndex = settings.get(KIBANA_CONFIG_INDEX_NAME, DEFAULT_USER_PROFILE_PREFIX);
        this.searchGuardIndex = settings.get(SEARCHGUARD_CONFIG_INDEX_NAME, DEFAULT_SECURITY_CONFIG_INDEX);
        this.kibanaVersion = settings.get(KIBANA_CONFIG_VERSION, DEFAULT_KIBANA_VERSION);
        this.kbnVersionHeader = settings.get(KIBANA_VERSION_HEADER, DEFAULT_KIBANA_VERSION_HEADER);
        this.enabled = settings.getAsBoolean(OPENSHIFT_DYNAMIC_ENABLED_FLAG, OPENSHIFT_DYNAMIC_ENABLED_DEFAULT);
        this.reWriteEnabled = settings.getAsBoolean(OPENSHIFT_KIBANA_REWRITE_ENABLED_FLAG,
                OPENSHIFT_KIBANA_REWRITE_ENABLED_DEFAULT);

        LOGGER.info("Using kibanaIndexMode: '{}'", this.kibanaIndexMode);
        LOGGER.debug("searchGuardIndex: {}", this.searchGuardIndex);
        LOGGER.debug("roleStrategy: {}", this.roleStrategy);

    }

    public Settings getSettings() {
        return this.settings;
    }

    public String getRoleStrategy() {
        return this.roleStrategy;
    }

    public String getKibanaIndexMode() {
        return kibanaIndexMode;
    }

    public String getCdmProjectPrefix() {
        return cdmProjectPrefix;
    }

    public String getDefaultKibanaIndex() {
        return defaultKibanaIndex;
    }

    public String getSearchGuardIndex() {
        return searchGuardIndex;
    }

    public String getKibanaVersion() {
        return kibanaVersion;
    }

    public String getKbnVersionHeader() {
        return kbnVersionHeader;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public Boolean isKibanaRewriteEnabled() {
        return reWriteEnabled;
    }

    public void setKibanaIndexMode(String kibanaIndexMode) {
        this.kibanaIndexMode = kibanaIndexMode;
    }
}
