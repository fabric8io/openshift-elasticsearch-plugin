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

public interface KibanaIndexMode {
    /**
     * The setting that determines the kibana index is used by users.  Valid values are one of the following:
     * 
     *   * unique (Default) - Each user gets a unique index for kibana visualizations (e.g. .kibana.USER_UUID)
     *   * shared_ops       - Users who are in an ops role will share an index (e.g. kibana_ops) while non ops users will 
     *                        have a unique index (e.g. .kibana.USER_UUID)
     *   * shared_non_ops   - Users who are in an ops role will share an index (e.g. kibana) while non-ops users will 
     *                        share an index (e.g. .kibana_non_ops)                      
     */
    static final String OPENSHIFT_KIBANA_INDEX_MODE = "openshift.kibana.index.mode";
    
    static final String UNIQUE = "unique";
    static final String SHARED_OPS = "shared_ops";
    static final String SHARED_NON_OPS = "shared_non_ops";
    static final String DEFAULT_MODE = UNIQUE;
}