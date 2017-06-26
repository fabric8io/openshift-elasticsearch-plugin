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

package io.fabric8.elasticsearch.util;

import org.apache.commons.lang.ObjectUtils;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestRequest;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;

public class RequestUtils implements ConfigurationSettings  {

    private String proxyUserHeader;

    @Inject
    public RequestUtils(final Settings settings) {
        this.proxyUserHeader = settings.get(SEARCHGUARD_AUTHENTICATION_PROXY_HEADER, DEFAULT_AUTH_PROXY_HEADER);
    }
    
    public String getUser(RestRequest request) {
        return (String) ObjectUtils.defaultIfNull(request.header(proxyUserHeader), "");
    }
}
