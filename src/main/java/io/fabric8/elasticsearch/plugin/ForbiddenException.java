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

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.rest.RestStatus;

public class ForbiddenException extends ElasticsearchException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4274241657687277455L;
	
	public ForbiddenException(Throwable e){
		super("Request is forbidden because user does not have the proper access to the requested index", e);
	}

	@Override
	public RestStatus status() {
		return RestStatus.FORBIDDEN;
	}
}
