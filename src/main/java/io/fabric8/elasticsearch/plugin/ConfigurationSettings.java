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

public interface ConfigurationSettings extends KibanaIndexMode{

    /** Searchguard settings here **/
    static final String SEARCHGUARD_AUTHENTICATION_PROXY_HEADER = "searchguard.authentication.proxy.header";
    static final String SEARCHGUARD_CONFIG_INDEX_NAME = "searchguard.config_index_name";
    static final String SEARCHGUARD_ROLE_TYPE = "roles";
    static final String SEARCHGUARD_MAPPING_TYPE = "rolesmapping";
    static final String SEARCHGUARD_CONFIG_ID = "0";
    static final String[] SEARCHGUARD_INITIAL_CONFIGS = new String[] { "config", "roles", "rolesmapping",
        "actiongroups", "internalusers" };
    static final String SEARCHGUARD_ADMIN_DN = "searchguard.authcz.admin_dn";

    static final String SG_CONFIG_SETTING_PATH = "searchguard.config.path";
    static final String SG_CLIENT_KS_PATH = "openshift.searchguard.keystore.path";
    static final String SG_CLIENT_TS_PATH = "openshift.searchguard.truststore.path";
    static final String SG_CLIENT_KS_PASS = "openshift.searchguard.keystore.password";
    static final String SG_CLIENT_TS_PASS = "openshift.searchguard.truststore.password";
    static final String SG_CLIENT_KS_TYPE = "openshift.searchguard.keystore.type";
    static final String SG_CLIENT_TS_TYPE = "openshift.searchguard.truststore.type";

    static final String DEFAULT_SEARCHGUARD_ADMIN_DN = "CN=system.admin,OU=client,O=client,L=Test,C=DE";

    static final String DEFAULT_SG_CONFIG_SETTING_PATH = "/opt/app-root/src/sgconfig/";
    static final String DEFAULT_SG_CLIENT_KS_PATH = "/usr/share/elasticsearch/config/admin.jks";
    static final String DEFAULT_SG_CLIENT_TS_PATH = "/usr/share/elasticsearch/config/logging-es.truststore.jks";
    static final String DEFAULT_SG_CLIENT_KS_PASS = "kspass";
    static final String DEFAULT_SG_CLIENT_TS_PASS = "tspass";
    static final String DEFAULT_SG_CLIENT_KS_TYPE = "JKS";
    static final String DEFAULT_SG_CLIENT_TS_TYPE = "JKS";
    /** Searchguard settings here **/

    /** Kibana settings here **/
    static final String KIBANA_CONFIG_INDEX_NAME = "kibana.config_index_name";
    static final String KIBANA_CONFIG_VERSION = "kibana.version";
    static final String KIBANA_VERSION_HEADER = "kibana.version.header";

    static final String DEFAULT_KIBANA_VERSION = "5.6.9";
    static final String DEFAULT_KIBANA_VERSION_HEADER = "kbn-version";
    /** Kibana settings here **/

    /**
     * The maximum time time in milliseconds to wait for SearchGuard to sync the
     * ACL from a write from this plugin until load by searchguard
     */
    /** OpenShift settings here **/
    static final String OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_APP = "io.fabric8.elasticsearch.kibana.mapping.app";
    static final String OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_OPERATIONS = "io.fabric8.elasticsearch.kibana.mapping.ops";
    static final String OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_EMPTY = "io.fabric8.elasticsearch.kibana.mapping.empty";
    static final String OPENSHIFT_ES_USER_PROFILE_PREFIX = "io.fabric8.elasticsearch.acl.user_profile_prefix";

    static final String OPENSHIFT_CONFIG_OPS_PROJECTS = "openshift.operations.project.names";
    static final String[] DEFAULT_OPENSHIFT_OPS_PROJECTS = new String[] { "default", "openshift", "openshift-infra",
        "kube-system" };
    static final String OPENSHIFT_REQUEST_CONTEXT = "x-openshift-request-context";

    static final String DEFAULT_AUTH_PROXY_HEADER = "X-Proxy-Remote-User";
    static final String DEFAULT_SECURITY_CONFIG_INDEX = "searchguard";
    static final String DEFAULT_USER_PROFILE_PREFIX = ".kibana";
    static final String[] DEFAULT_WHITELISTED_USERS = new String[] { "$logging.$infra.$fluentd",
        "$logging.$infra.$kibana", "$logging.$infra.$curator" };
    
    static final String [] DEFAULT_KIBANA_OPS_INDEX_PATTERNS = new String[] {
        ".operations.*", ".orphaned.*", "project.*", ".all" 
    };
    
    /**
     * The configurations for enabling/disabling portions of this plugin
     * defaults to 'true' => enabled.
     *
     * This need came from integrating with APIMan -- we needed to seed our
     * initial ACL but didn't need to dynamically update the ACL or rewrite our
     * Kibana index.
     */
    static final String OPENSHIFT_DYNAMIC_ENABLED_FLAG = "openshift.acl.dynamic.enabled";

    static final String OPENSHIFT_ACL_EXPIRE_IN_MILLIS = "openshift.acl.expire_in_millis";

    /**
     * The strategy to use for generating roles and role mappings
     */
    static final String OPENSHIFT_ACL_ROLE_STRATEGY = "openshift.acl.role_strategy";
    static final String DEFAULT_ACL_ROLE_STRATEGY = "user";
    
    static final String OPENSHIFT_KIBANA_REWRITE_ENABLED_FLAG = "openshift.kibana.rewrite.enabled";
    
    /**
     * List of index patterns to create for operations users
     */
    static final String OPENSHIFT_KIBANA_OPS_INDEX_PATTERNS = "openshift.kibana.ops_index_patterns";

    
    static final boolean OPENSHIFT_DYNAMIC_ENABLED_DEFAULT = true;
    static final boolean OPENSHIFT_KIBANA_REWRITE_ENABLED_DEFAULT = true;

    static final String OPENSHIFT_CONFIG_PROJECT_INDEX_PREFIX = "openshift.config.project_index_prefix";
    static final String OPENSHIFT_DEFAULT_PROJECT_INDEX_PREFIX = "";

}
