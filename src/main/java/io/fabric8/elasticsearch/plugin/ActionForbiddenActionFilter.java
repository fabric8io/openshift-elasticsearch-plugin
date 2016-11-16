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
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

/**
 * ActionFilter to intercept the response and return
 * a proper 403 exception
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class ActionForbiddenActionFilter implements ActionFilter{

	private ESLogger logger;

	@Inject
	public ActionForbiddenActionFilter(){
		this.logger = Loggers.getLogger(getClass());
	}
	
	@Override
	public int order() {
		return Integer.MIN_VALUE;
	}
	
	@Override
	public void apply(String action, ActionRequest request, final ActionListener listener, ActionFilterChain chain) {
		chain.proceed(action, request, new ActionListener(){

			@Override
			public void onResponse(Object response) {
				listener.onResponse(response);
			}

			@Override
			public void onFailure(Throwable e) {
				if(e != null && e.getMessage() != null && e.getMessage().contains("is forbidden due to")){
					logger.debug("SearchGuard 403", e);
					listener.onFailure(new ForbiddenException(e));
				}else{
					listener.onFailure(e);
				}
			}
			
		});
	}
	
	@Override
	public void apply(String action, ActionResponse response, ActionListener listener, ActionFilterChain chain) {
		chain.proceed(action, response, listener);
	}

}
