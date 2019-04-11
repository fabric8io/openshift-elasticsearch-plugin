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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
    private final Set<String> opsIndexPatterns;
    private final long expireInMillis;
    private final Settings settings;
    private final String masterUrl;
    private final Boolean isTrustCerts;
    private final String openshiftCaPath;
    
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
        this.opsIndexPatterns = new HashSet<String>(Arrays.asList(settings.getAsArray(OPENSHIFT_KIBANA_OPS_INDEX_PATTERNS, DEFAULT_KIBANA_OPS_INDEX_PATTERNS)));
        this.expireInMillis = settings.getAsLong(OPENSHIFT_ACL_EXPIRE_IN_MILLIS, new Long(1000 * 60));

        this.masterUrl = settings.get(OPENSHIFT_MASTER);
        this.openshiftCaPath = settings.get(OPENSHIFT_CA_PATH);
        // Do not overwrite default K8S behavior
        if (settings.get(OPENSHIFT_TRUST_CERT) != null) {
            this.isTrustCerts = settings.getAsBoolean(OPENSHIFT_TRUST_CERT, true);
        } else {
            this.isTrustCerts = null;
        }

        LOGGER.info("Using kibanaIndexMode: '{}'", this.kibanaIndexMode);
        LOGGER.debug("searchGuardIndex: {}", this.searchGuardIndex);
        LOGGER.debug("roleStrategy: {}", this.roleStrategy);

    }
    
    public Settings getSettings() {
        return this.settings;
    }
    
    public long getACLExpiresInMillis() {
        return expireInMillis;
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

    public void setKibanaIndexMode(String kibanaIndexMode) {
        this.kibanaIndexMode = kibanaIndexMode;
    }
    
    public Set<String> getKibanaOpsIndexPatterns() {
        return opsIndexPatterns;
    }

    public String getMasterUrl() {
        return masterUrl;
    }

    public String getOpenshiftCaPath() {
        return openshiftCaPath;
    }

    public Boolean isTrustCerts() {
        return isTrustCerts;
    }
}
