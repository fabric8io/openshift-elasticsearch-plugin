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

public interface ConfigurationSettings {

	/** Searchguard settings here **/
	static final String SEARCHGUARD_AUTHENTICATION_PROXY_HEADER = "searchguard.authentication.proxy.header";
	static final String SEARCHGUARD_CONFIG_INDEX_NAME = "searchguard.config_index_name";
	static final String SEARCHGUARD_ROLE_TYPE = "roles";
	static final String SEARCHGUARD_MAPPING_TYPE = "rolesmapping";
	static final String SEARCHGUARD_CONFIG_ID = "0";
	static final String[] SEARCHGUARD_INITIAL_CONFIGS = new String[] { "config", "roles", "rolesmapping",
			"actiongroups" };
	static final String SEARCHGUARD_ADMIN_DN = "searchguard.authcz.admin_dn";

	static final String SG_ACTION_ALL = "indices:*";
	static final String SG_ACTION_READ = "indices:data/read*";
	static final String SG_ACTION_WRITE = "indices:data/write*";
	static final String SG_ACTION_CREATE_INDEX = "indices:admin/create";
	static final String SG_ACTION_CLUSTER_ALL = "cluster:*";
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

	static final String DEFAULT_KIBANA_VERSION = "4.5.1";
	static final String DEFAULT_KIBANA_VERSION_HEADER = "kbn-version";
	/** Kibana settings here **/

	/**
	 * The maximum time time in milliseconds to wait for SearchGuard to sync the
	 * ACL from a write from this plugin until load by searchguard
	 */
	/** OpenShift settings here **/
	static final String OPENSHIFT_ES_ACL_DELAY_IN_MILLIS = "io.fabric8.elasticsearch.acl.sync_delay_millis";
	static final String OPENSHIFT_ES_USER_PROFILE_PREFIX = "io.fabric8.elasticsearch.acl.user_profile_prefix";
	static final String OPENSHIFT_WHITELISTED_USERS = "io.fabric8.elasticsearch.authentication.users";
	static final String OPENSHIFT_ROLES = "X-OpenShift-Roles";
	static final String OPENSHIFT_ALLOW_CLUSTER_READER = "openshift.operations.allow_cluster_reader";

	static final String OPENSHIFT_CONFIG_OPS_PROJECTS = "openshift.operations.project.names";
	static final String[] DEFAULT_OPENSHIFT_OPS_PROJECTS = new String[] { "default", "openshift", "openshift-infra",
			"kube-system" };

	static final String DEFAULT_AUTH_PROXY_HEADER = "X-Proxy-Remote-User";
	static final String DEFAULT_SECURITY_CONFIG_INDEX = "searchguard";
	static final String DEFAULT_USER_PROFILE_PREFIX = ".kibana";
	static final String[] DEFAULT_WHITELISTED_USERS = new String[] { "$logging.$infra.$fluentd",
			"$logging.$infra.$kibana", "$logging.$infra.$curator" };
	
	static final boolean DEFAULT_OPENSHIFT_ALLOW_CLUSTER_READER = false;

	/**
	 * The configurations for the initial ACL as well as what the .operations
	 * index consists of
	 */
	static final String OPENSHIFT_CONFIG_ACL_BASE = "openshift.acl.users.";
	static final String OPENSHIFT_CONFIG_ACL_NAMES = OPENSHIFT_CONFIG_ACL_BASE + "names";

	/**
	 * The configurations for enabling/disabling portions of this plugin
	 * defaults to 'true' => enabled.
	 *
	 * This need came from integrating with APIMan -- we needed to seed our
	 * initial ACL but didn't need to dynamically update the ACL or rewrite our
	 * Kibana index.
	 */
	static final String OPENSHIFT_DYNAMIC_ENABLED_FLAG = "openshift.acl.dynamic.enabled";
	static final String OPENSHIFT_KIBANA_REWRITE_ENABLED_FLAG = "openshift.kibana.rewrite.enabled";

	static final boolean OPENSHIFT_DYNAMIC_ENABLED_DEFAULT = true;
	static final boolean OPENSHIFT_KIBANA_REWRITE_ENABLED_DEFAULT = true;

	static final String OPENSHIFT_CONFIG_USE_COMMON_DATA_MODEL = "openshift.config.use_common_data_model";
	static final boolean OPENSHIFT_DEFAULT_USE_COMMON_DATA_MODEL = false;

	static final String OPENSHIFT_CONFIG_PROJECT_INDEX_PREFIX = "openshift.config.project_index_prefix";
	static final String OPENSHIFT_DEFAULT_PROJECT_INDEX_PREFIX = "";

	static final String OPENSHIFT_CONFIG_TIME_FIELD_NAME = "openshift.config.time_field_name";
	static final String OPENSHIFT_DEFAULT_TIME_FIELD_NAME = "time";
}
