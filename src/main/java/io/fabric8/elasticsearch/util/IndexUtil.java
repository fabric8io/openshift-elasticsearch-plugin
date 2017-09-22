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

import java.util.HashSet;
import java.util.Set;

/**
 * A utility for manipulating index strings
 *
 */
public class IndexUtil {
    
    /**
     * Take a set of indices and remove the date suffix
     * project.logging.ae5ca3cb-890e-11e7-b9c2-52540050d5ea.2017.09.06 becomes:
     * project.logging.ae5ca3cb-890e-11e7-b9c2-52540050d5ea.*
     * 
     * @param value     the value to use in place of the date
     * @param indices   the set of indices to process
     * @return  A set of the indices with modified values
     */
    public Set<String> replaceDateSuffix(final String value, final Set<String> indices){
        Set<String> modified = new HashSet<>();
        final String sub = "." + value;
        for (String index : indices) {
            modified.add(index.replaceFirst("\\.\\d\\d\\d\\d\\.\\d\\d.\\d\\d$", sub));
        }
        return modified;
    }

}
