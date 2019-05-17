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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestRequest;
import org.junit.Before;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginSettings;

public class RequestUtilsTest {
    
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String PROXY_HEADER = "aValue";
    private static final String USER = "auser";
    private RequestUtils util;
    
    @Before
    public void setUp() throws Exception {
        Settings settings = Settings.builder().put(ConfigurationSettings.SEARCHGUARD_AUTHENTICATION_PROXY_HEADER, PROXY_HEADER).build();
        PluginSettings pluginSettings = new PluginSettings(settings);
        util = new RequestUtils(pluginSettings, null);
    }

    @Test
    public void testGetUserFromHeader() {
        Map<String, List<String>> headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        headers.put(PROXY_HEADER, Arrays.asList(USER));
        RestRequest request = new TestRestRequest(headers);
        
        assertEquals(USER, util.getUser(request));
    }

    @Test
    public void testModifyContentType() {
        Map<String, List<String>> headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        headers.put(CONTENT_TYPE, Arrays.asList("application/x-ndjson"));
        RestRequest request = new TestRestRequest(headers);
        
        OpenshiftRequestContext context = new OpenshiftRequestContextFactory.OpenshiftRequestContext("foo", null, false, new HashSet<>(), null, null, Collections.emptyList());
        RestRequest modifyRequest = util.modifyRequest(request, context , null);
        assertEquals("application/json", modifyRequest.header(CONTENT_TYPE));
    }

    @Test
    public void testModifyContentTypeWhenContextIsEmpty() {
        Map<String, List<String>> headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        headers.put(CONTENT_TYPE, Arrays.asList("application/x-ndjson"));
        RestRequest request = new TestRestRequest(headers);
        
        RestRequest modifyRequest = util.modifyRequest(request, OpenshiftRequestContext.EMPTY , null);
        assertEquals("", request, modifyRequest);
    }
    
}
