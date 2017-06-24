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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestRequest;
import org.junit.Before;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;

public class RequestUtilsTest {
    
    private static final String PROXY_HEADER = "aValue";
    private static final String USER = "auser";
    private RequestUtils util;
    
    @Before
    public void setUp() throws Exception {
        Settings settings = Settings.builder().put(ConfigurationSettings.SEARCHGUARD_AUTHENTICATION_PROXY_HEADER, PROXY_HEADER).build();
        util = new RequestUtils(settings);
    }

    @Test
    public void testGetUserFromHeader() {
        RestRequest request = mock(RestRequest.class);
        when(request.header(eq(PROXY_HEADER))).thenReturn(USER);
        
        assertEquals(USER, util.getUser(request));
    }
    
}
