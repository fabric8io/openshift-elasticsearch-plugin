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

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class IndexUtilTest {

    @Test
    public void testReplaceDateSuffix() {
        IndexUtil util = new IndexUtil();
        Set<String> indices = new HashSet<>();
        indices.add("project.logging.ae5ca3cb-890e-11e7-b9c2-52540050d5ea.2017.09.06");
        indices.add("project.logging.ae5ca3cb-890e-11e7-b9c2-52540050d5ea.2017.09.09");
        Set<String> replaced = util.replaceDateSuffix("*", indices);
        assertEquals(1, replaced.size());
        assertEquals("project.logging.ae5ca3cb-890e-11e7-b9c2-52540050d5ea.*", (String)replaced.toArray()[0]);
    }

}
