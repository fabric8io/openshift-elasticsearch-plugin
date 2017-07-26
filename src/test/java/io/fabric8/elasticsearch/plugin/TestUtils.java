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

import org.yaml.snakeyaml.Yaml;

public class TestUtils {


    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildMap(StringReader reader) {
        return (Map<String, Object>) new Yaml().load(reader);
    }
    
    
    public static void assertJson(String message, String exp, Map<String, Object> act) throws Exception {
        assertEquals(message, exp, new Yaml().dump(act));
    }

}
