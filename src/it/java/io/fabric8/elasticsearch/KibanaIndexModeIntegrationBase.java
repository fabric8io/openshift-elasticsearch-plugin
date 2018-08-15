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

package io.fabric8.elasticsearch;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.junit.Before;

import okhttp3.Response;


public abstract class KibanaIndexModeIntegrationBase extends ElasticsearchIntegrationTest {


    @SuppressWarnings("unchecked")
    protected void assertThatMessageEquals(String message) throws Exception {
        Response response = (Response) testContext.get(RESPONSE);
        Map<String, Object> source = XContentHelper.convertToMap(JsonXContent.jsonXContent, response.body().byteStream(),false);
        Map<String, Object> doc =  (Map<String, Object>) source.get("_source");
        assertEquals("Exp. Message content to be equal", message, doc.get("msg"));
    }
    
    @Before
    public void setup() throws Exception {
        givenDocumentIsIndexed("project.multi-tenancy-1.uuid.1970.01.01", "test", "0", "multi-tenancy-1-doc0");
        givenDocumentIsIndexed("project.multi-tenancy-2.uuid.1970.01.01", "test", "0", "multi-tenancy-2-doc0");
        givenDocumentIsIndexed("project.multi-tenancy-3.uuid.1970.01.01", "test", "0", "multi-tenancy-3-doc0");
    }
 
}
