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

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Test;

/**
 * This IT runs against clean ES cluster without any plugins, it is used for
 * simple "smoke-like" test. If this test passes without any errors and warnings
 * then it means we managed to setup Java Security Manager correctly.
 */
@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.TEST)
public class SmokeElasticsearchIntegrationTest extends ElasticsearchWithoutPluginsSupport {

    @Test
    public void testClusterIsEmpty() throws Exception {

        SearchResponse searchResponse = client().prepareSearch().setQuery(QueryBuilders.matchAllQuery()).get();
        ensureGreen();
        assertHitCount(searchResponse, 0);

        ClusterHealthResponse clusterHealthResponse = client().admin().cluster().health(
            new ClusterHealthRequest()
        ).get();

        //this.logger.info("Nodes settings: \n{}", clusterHealthResponse.toString());
        assertTrue(clusterHealthResponse.getIndices().isEmpty());
    }
}
