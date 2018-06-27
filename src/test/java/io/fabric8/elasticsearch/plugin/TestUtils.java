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

import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.Map;
import java.util.TreeMap;

import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.yaml.snakeyaml.Yaml;

public class TestUtils {


    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildMap(StringReader reader) {
        return (Map<String, Object>) new Yaml().load(reader);
    }
    
    public static void assertYaml(String message, String exp, ToXContent content) throws Exception {
        XContentBuilder builder = XContentFactory.yamlBuilder();
        builder.startObject();
        content.toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();
        Map<String, Object> act = XContentHelper.convertToMap(builder.bytes(), true, XContentType.YAML).v2();
        assertEquals(message, exp, new Yaml().dump(new TreeMap<>(act)));
    }

}
