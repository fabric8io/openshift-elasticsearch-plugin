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

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.junit.Before;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.KibanaIndexMode;
import io.fabric8.elasticsearch.plugin.kibana.KibanaUtils;

/*
 * This integration test verifies the ability (or not) to access Elasticsearch
 * depending upon what credentials are provided
 */
public class GeneralUserWorkflowIntegrationTest extends ElasticsearchIntegrationTest {


    @Before
    public void setup() throws Exception {
    }

    /*
     * This test verifies a basic nonadmin workflow, specifically to address 
     * https://bugzilla.redhat.com/show_bug.cgi?id=1679613.
     * org.elasticsearch.ElasticsearchException: org.elasticsearch.action.ActionRequestValidationException: Validation Failed: 1: id is missing;
     * at io.fabric8.elasticsearch.plugin.PluginClient.execute(PluginClient.java:322) ~[openshift-elasticsearch-plugin-5.6.13.2-redhat-1.jar:?]
     * at io.fabric8.elasticsearch.plugin.PluginClient.documentExists(PluginClient.java:238) ~[openshift-elasticsearch-plugin-5.6.13.2-redhat-1.jar:?]
     * at io.fabric8.elasticsearch.plugin.kibana.KibanaSeed.setDashboards(KibanaSeed.java:80) ~[openshift-elasticsearch-plugin-5.6.13.2-redhat-1.jar:?]
     * 
     * where its possible to have a defaultIndex but the value is null
     */
    @Test
    public void testNonadminUserRequestWorkflow() throws Exception {
        final String userName = "CN=Foo Bar,OU=People,OU=org-cs-0,DC=org-cs-0,DC=iuk,DC=local";
        //artificially seed user's kibanaIndex
        String kibanaIndex = getKibanaIndex(KibanaIndexMode.UNIQUE, userName, false);
        XContentBuilder contentBuilder = XContentFactory.jsonBuilder()
                .startObject()
                    .field("defaultIndex",(String)null)
                .endObject();
        givenDocumentIsIndexed(kibanaIndex, "config",  ConfigurationSettings.DEFAULT_KIBANA_VERSION, contentBuilder);
        givenDocumentIsIndexed(kibanaIndex, KibanaUtils.INDICIES_TYPE, "project.foo.uid.*", "bogus pattern");

        givenUserIsNotClusterAdmin(userName);
        givenUserIsAdminForProject("foo","uid");

        whenGettingDocument(String.format("%s/_count", formatProjectIndexPattern("foo", "uid")));
        assertThatResponseIsSuccessful();

    }
}
