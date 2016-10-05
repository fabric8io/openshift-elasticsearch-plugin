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

import org.junit.Test;

public class ProxyUsernameIntegrationTest extends ElasticsearchIntegrationTest {

    @Test
    public void testUserWithSimpleNameCreatesProfileIndex() throws Exception {
        startES();

        // test admin user
        givenUserIsClusterAdmin("admin");
        givenUserIsAdminForProjects("logging", "openshift");
        RequestRunner runner = new RequestRunnerBuilder().username("admin").build();
        HttpResponse response = runner.run("_cat/indices");
        assertEquals("Exp. admin user to list indices", 200, response.getStatusCode());

        givenUserIsClusterAdmin("somerandomuser");
        givenUserIsAdminForProjects("myproject");
        runner = new RequestRunnerBuilder().username("somerandomuser").build();
        response = runner.run("_cat/indices");
        assertEquals("Exp. somerandom user to be unable to list indices", 403, response.getStatusCode());
    }
}
