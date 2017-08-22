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

package io.fabric8.elasticsearch.plugin.kibana;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.index.get.GetResult;
import org.junit.Before;
import org.junit.Test;

public class GetResultBuilderTest {

    private GetResultBuilder builder;
    private GetResponse response;

    @Before
    public void setUp() throws Exception {
        this.builder = new GetResultBuilder();
        this.response = mock(GetResponse.class);
        
        when(response.getType()).thenReturn("type");
        when(response.getId()).thenReturn("id");
        when(response.getVersion()).thenReturn(123L);
        when(response.isExists()).thenReturn(true);
    }
    
    private void givenAnInitializedBuilder() {
        builder.response(response).index("foo").replacedIndex("bar");
    }

    @Test
    public void testBuildWhenResponseFieldsAreNotNull() {
        Object [] values = new String [] {"bar"};
        GetField field = new GetField("aField", new ArrayList<Object>(Arrays.asList(values)));
        Map<String, GetField> fields = new HashMap<>();
        fields.put("aField", field);
        when(response.getFields()).thenReturn(fields );
        when(response.isSourceEmpty()).thenReturn(true);
        givenAnInitializedBuilder();

        GetResult result = builder.build();
        assertNotNull(result);
    }
    
    @Test
    public void testBuildWhenSourceRefIsNotNull() {
        givenAnInitializedBuilder();
        when(response.isSourceEmpty()).thenReturn(false);
        BytesArray source = new BytesArray("bar");
        when(response.getSourceAsBytesRef()).thenReturn(source);
        GetResult result = builder.build();
        assertNotNull(result);
        assertEquals("foo",result.internalSourceRef().utf8ToString());
    }
    
    @Test
    public void testBuildWhenIndexIsNull() {
        assertNotNull(builder.response(response).replacedIndex("foo").build());
    }

    @Test
    public void testBuildWhenReplacedIndexIsNull() {
        assertNotNull(builder.response(response).index("foo").build());
    }
    
    @Test
    public void testBuildWhenResponseIndexIsNull() {
        assertNotNull(builder.replacedIndex("bar").index("foo").build());
    }

}
