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
package io.fabric8.elasticsearch.plugin.acl;

import java.lang.reflect.Method;

import org.apache.commons.lang.ArrayUtils;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.support.ActionFilter.Simple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;

/**
 * ActionFilter to recognize when the SearchGuard ACL
 * 
 * @author jeff.cantrill
 *
 *
 */
@SuppressWarnings("rawtypes")
public class SearchGuardACLRequestActionFilter extends Simple implements ConfigurationSettings{

	
	private final ACLNotifierService notifier;
	private final String searchGuardIndex;

	@Inject
	protected SearchGuardACLRequestActionFilter(Settings settings, final ACLNotifierService notifier) {
		super(settings);
		this.notifier = notifier;
		this.searchGuardIndex = settings.get(SEARCHGUARD_CONFIG_INDEX_NAME, DEFAULT_SECURITY_CONFIG_INDEX);
	}

	@Override
	public int order() {
		return Integer.MIN_VALUE;
	}

	@Override
	protected boolean apply(String action, ActionRequest request, ActionListener listener) {
		if(request instanceof GetRequest){
			final GetRequest ir = (GetRequest) request;
			if(isACLRequest(ir)){
				logger.debug("Received ActionRequest for the SearchGuard ACL with context: '{}'", request.getContext());
				//ignore context if load from our filter
				if(isRequestedByUs(ir)){
					logger.debug("SearchGuard ACL being loaded by OpenShift ElasticSearch plugin. Skipping filter");
					return true;
				}
				notifier.notify(action);
			}
		}
		return true;
	}
	
	private boolean isRequestedByUs(GetRequest request){
		return ACL_FILTER_ID.equals(request.getFromContext(SearchGuardACLActionRequestListener.OS_ES_REQ_ID, ""));
	}
	
	private boolean isACLRequest(GetRequest request){
		return ArrayUtils.contains(request.indices(),searchGuardIndex) && includesProperyValue(request, "type", SEARCHGUARD_TYPE) && includesProperyValue(request, "id", SEARCHGUARD_ID);
	}
	
	@Override
	protected boolean apply(String action, ActionResponse response, ActionListener listener) {
		return true;
	}
	
	/*
	 * Courtesy of com.floragunn.searchguard.filter.SearchGuardActionFilter
	 */
    private boolean includesProperyValue(final IndicesRequest request, final String property, final String expValue) {
        try {
            final Method method = request.getClass().getDeclaredMethod(property);
            method.setAccessible(true);
            final String value = (String) method.invoke(request);
            return expValue.equals(value);
        } catch (final Exception e) {
            try {
                final Method method = request.getClass().getDeclaredMethod(property + "s");
                method.setAccessible(true);
                final String[] types = (String[]) method.invoke(request);
                return ArrayUtils.contains(types, expValue);
            } catch (final Exception e1) {
                logger.warn("Cannot determine {} for {} due to {}[s]() method not found", property, request, property);
            }

        }
        return false;
    }

}
