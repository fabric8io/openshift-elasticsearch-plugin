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


import io.fabric8.elasticsearch.plugin.acl.ACLNotifierService;
import io.fabric8.elasticsearch.plugin.acl.DefaultACLNotifierService;
import io.fabric8.elasticsearch.plugin.acl.DynamicACLFilter;
import io.fabric8.elasticsearch.plugin.acl.UserProjectCache;
import io.fabric8.elasticsearch.plugin.acl.UserProjectCacheMapAdapter;

import org.elasticsearch.common.inject.AbstractModule;

/**
 * The module controls loading and specific implementations
 * we want to use
 *
 */
public class OpenShiftElasticSearchModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ACLNotifierService.class)
			.to(DefaultACLNotifierService.class)
			.asEagerSingleton();
		
		bind(UserProjectCache.class)
			.to(UserProjectCacheMapAdapter.class)
			.asEagerSingleton();
		
		bind(DynamicACLFilter.class).asEagerSingleton();
	}

}
