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
	
	
	public static final String SEARCHGUARD_AUTHENTICATION_PROXY_HEADER = "searchguard.authentication.proxy.header";
	public static final String SEARCHGUARD_CONFIG_INDEX_NAME = "searchguard.config_index_name";
	
	/**
	 * The maximum time time in milliseconds to wait for SearchGuard to sync the ACL from 
	 * a write from this plugin until load by searchguard
	 */
	public static final String OPENSHIFT_ES_ACL_DELAY_IN_MILLIS = "io.fabric8.elasticsearch.acl.sync_delay_millis";
	public static final String OPENSHIFT_ES_USER_PROFILE_PREFIX = "io.fabric8.elasticsearch.acl.user_profile_prefix";
	
	public static final String DEFAULT_AUTH_PROXY_HEADER = "X-Authenticated-User";
	public static final String DEFAULT_SECURITY_CONFIG_INDEX = "searchguard";
	public static final String DEFAULT_USER_PROFILE_PREFIX = ".kibana";

	public static final int DEFAULT_ES_ACL_DELAY = 2500;

}
