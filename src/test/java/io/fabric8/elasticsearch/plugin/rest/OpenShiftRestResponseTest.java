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

package io.fabric8.elasticsearch.plugin.rest;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;

import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.KibanaIndexMode;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;

public class OpenShiftRestResponseTest implements ConfigurationSettings{

    private static final String KIBANA_INDEX = OpenshiftRequestContextFactory.getKibanaIndex(DEFAULT_USER_PROFILE_PREFIX, 
            KibanaIndexMode.UNIQUE, 
            "myusername", 
            false);
    
    private static final OpenshiftRequestContext CONTEXT = new OpenshiftRequestContext("myusername", 
            "mytoken", false, new HashSet<>(), KIBANA_INDEX, KibanaIndexMode.UNIQUE);
    private static final XContent XCONTENT = XContentType.JSON.xContent();
    
    private OpenShiftRestResponse whenCreatingResponseResponse(ToXContent content) throws Exception {
        RestResponse response = new BytesRestResponse(RestStatus.CREATED, content.toXContent(XContentBuilder.builder(XCONTENT), ToXContent.EMPTY_PARAMS));
        return new OpenShiftRestResponse(response, CONTEXT, DEFAULT_USER_PROFILE_PREFIX);
    }
    
    private XContentParser givenContentParser(String body) throws Exception {
        return XCONTENT.createParser(NamedXContentRegistry.EMPTY, String.format(body, KIBANA_INDEX)); 
    }
    
    private void thenResponseShouldBeModified(OpenShiftRestResponse osResponse, String expPattern) {
        assertEquals(String.format(expPattern, DEFAULT_USER_PROFILE_PREFIX), osResponse.content().utf8ToString());
    }
    
    @Test
    public void testSearchResponse() throws Exception {
        
        String body = "{\"took\":10,\"timed_out\":false,\"_shards\":{\"total\":5,\"successful\":5,\"skipped\":0,\"failed\":0},"
                + "\"hits\":{\"total\":2,\"max_score\":1.0,\"hits\":[{\"_index\":\"%1$s\",\"_type\":\"config\",\"_id\":\"0\","
                + "\"_score\":1.0,\"_source\":{\"key\":\"value\",\"version\":\"5.6.10\"}},{\"_index\":\"%1$s\",\"_type\":\"config\","
                + "\"_id\":\"2\",\"_score\":1.0,\"_source\":{\"key\":\"value\",\"version\":\"5.6.10\"}}]}}";
        
        XContentParser parser = XCONTENT.createParser(NamedXContentRegistry.EMPTY, String.format(body, KIBANA_INDEX));
        SearchResponse actionResponse = SearchResponse.fromXContent(parser);
        
        OpenShiftRestResponse osResponse = whenCreatingResponseResponse(actionResponse);
        thenResponseShouldBeModified(osResponse, body);
    }
    
    @Test
    public void testIndexResponse() throws Exception {
        
        String body = "{\"_index\":\"%s\",\"_type\":\"index-pattern\",\"_id\":\"0\",\"_version\":1,\"result\":\"created\",\"_shards\":"
                + "{\"total\":2,\"successful\":1,\"failed\":0},\"created\":true}";
        
        XContentParser parser = XCONTENT.createParser(NamedXContentRegistry.EMPTY, String.format(body, KIBANA_INDEX));
        IndexResponse actionResponse = IndexResponse.fromXContent(parser);
        
        OpenShiftRestResponse osResponse = whenCreatingResponseResponse(actionResponse);
        thenResponseShouldBeModified(osResponse, body);
    }

    @Test
    public void testGetResponse() throws Exception {
        
        String body = "{\"_index\":\"%s\",\"_type\":\"config\",\"_id\":\"0\",\"_version\":1,\"found\":true,\"_source\":{\"key\":\"value\",\"version\":\"5.6.10\"}}";
        
        XContentParser parser = givenContentParser(body);
        GetResponse actionResponse = GetResponse.fromXContent(parser);
        
        OpenShiftRestResponse osResponse = whenCreatingResponseResponse(actionResponse);
        thenResponseShouldBeModified(osResponse, body);
    }

    @Test
    public void testDeleteResponse() throws Exception {
        
        String body = "{\"found\":true,\"_index\":\"%s\""
                + ",\"_type\":\"config\",\"_id\":\"0\",\"_version\":2,\"result\":\"deleted\",\"_shards\":"
                + "{\"total\":2,\"successful\":1,\"failed\":0}}";
        
        XContentParser parser = givenContentParser(body);
        DeleteResponse actionResponse = DeleteResponse.fromXContent(parser);
        
        OpenShiftRestResponse osResponse = whenCreatingResponseResponse(actionResponse);
        thenResponseShouldBeModified(osResponse, body);
    }

    @Test
    public void testMGetResponse() throws Exception {
        
        String body = "{\"docs\":[{\"_index\":\"%1$s\",\"_type\":\"config\",\"_id\":\"0\",\"found\":true"
                + "},{\"_index\":\"%1$s\",\"_type\":\"config\","
                + "\"_id\":\"1\",\"found\":true}]}";
        MultiGetItemResponse [] items = new MultiGetItemResponse [2];
        for (int i = 0; i < items.length; i++) {
            String itemBody =  "{\"_index\":\"%1$s\",\"_type\":\"config\",\"_id\":\"" + i + "\",\"found\":true}";
            XContentParser parser = givenContentParser(itemBody);
            items[i] = new MultiGetItemResponse(GetResponse.fromXContent(parser), null);
        }
        
        MultiGetResponse actionResponse = new MultiGetResponse(items);
        
        OpenShiftRestResponse osResponse = whenCreatingResponseResponse(actionResponse);
        thenResponseShouldBeModified(osResponse, body);
    }

}
