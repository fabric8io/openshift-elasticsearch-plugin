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

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.get.GetResult;

public class KibanaUserReindexAction implements ActionFilter {

	private final ESLogger logger;
	private final String kibanaIndex;
	
	@Inject
	public KibanaUserReindexAction(final Settings settings, final ClusterService clusterService, final Client client) {
		this.logger = Loggers.getLogger(KibanaUserReindexAction.class);
		this.kibanaIndex = settings.get(ConfigurationSettings.KIBANA_CONFIG_INDEX_NAME, 
				ConfigurationSettings.KIBANA_CONFIG_INDEX_NAME);
		
		logger.debug("Initializing KibanaUserReindexAction");
	}

	@Override
	public int order() {
		// We want this to be the last in the chain
		return Integer.MAX_VALUE;
	}

	@Override
	public void apply(String action, ActionRequest request,
			ActionListener listener, ActionFilterChain chain) {
		chain.proceed(action, request, listener);
	}

	@Override
	public void apply(String action, ActionResponse response,
			ActionListener listener, ActionFilterChain chain) {
		
		logger.debug("Response with Action '{}' and class '{}'", action, response.getClass());
		
		if ( response instanceof IndexResponse ) {
			final IndexResponse ir = (IndexResponse) response;
			String index = getIndex(ir);
			
			response = new IndexResponse(index, ir.getType(), ir.getId(), ir.getVersion(), ir.isCreated());
		}
		else if ( response instanceof GetResponse ) {
			response = new GetResponse(buildNewResult((GetResponse) response));
		}
		else if ( response instanceof DeleteResponse ) {
			final DeleteResponse dr = (DeleteResponse) response;
			String index = getIndex(dr);
			
			response = new DeleteResponse(index, dr.getType(), dr.getId(), dr.getVersion(), dr.isFound());
		}
		
		chain.proceed(action, response, listener);
	}
	
	private GetResult buildNewResult(GetResponse response) {
		String index = getIndex(response);
		
		GetResult getResult = new GetResult(index, response.getType(), response.getId(), response.getVersion(),
				response.isExists(), 
				(!response.isSourceEmpty()) ? response.getSourceAsBytesRef() : null, 
				response.getFields());
		
		return getResult;
	}
	
	private boolean isKibanaUserIndex(String index) {
		return (index.startsWith(kibanaIndex) && !index.equalsIgnoreCase(kibanaIndex));
	}
	
	private String getIndex(ActionResponse response) {
		String index = "";
		
		if ( response instanceof IndexResponse )
			index = ((IndexResponse) response).getIndex();
		else if ( response instanceof GetResponse )
			index = ((GetResponse)response).getIndex();
		else if ( response instanceof DeleteResponse )
			index = ((DeleteResponse)response).getIndex();
		
		if ( isKibanaUserIndex(index) ) {
			index = kibanaIndex;
		}
		
		return index;
	}
}
