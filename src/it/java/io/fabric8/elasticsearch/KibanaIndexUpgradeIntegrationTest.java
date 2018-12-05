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

import java.util.Arrays;
import java.util.Map;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.KibanaIndexMode;
import io.fabric8.elasticsearch.plugin.kibana.KibanaUtils;
import okhttp3.Response;

public class KibanaIndexUpgradeIntegrationTest extends KibanaIndexModeIntegrationBase {

    @Override
    protected Settings additionalNodeSettings() {
        return Settings.builder()
                .put(KibanaIndexMode.OPENSHIFT_KIBANA_INDEX_MODE, KibanaIndexMode.UNIQUE)
                .putArray(ConfigurationSettings.OPENSHIFT_KIBANA_OPS_INDEX_PATTERNS, Arrays.asList(".all"))
                .build();
    }
    
    @Test
    public void testUpgradeOfKibanaIndexForNonOps() throws Exception {
        final String userName = "nonadminuser";
        //artificially seed user's kibanaIndex
        String kibanaIndex = getKibanaIndex(KibanaIndexMode.UNIQUE, userName, false);
        XContentBuilder contentBuilder = XContentFactory.jsonBuilder()
                .startObject()
                    .field("defaultIndex","project.foo.uid.*")
                .endObject();
        givenDocumentIsIndexed(kibanaIndex, "config", OLD_KIBANA_VERSION, contentBuilder);
        givenDocumentIsIndexed(kibanaIndex, KibanaUtils.INDICIES_TYPE, "project.foo.uid.*", "bogus pattern");
        givenDocumentIsIndexed("project.foo.uid.1970.01.01", "test","0","testMsg");
        givenDocumentIsRemoved(".kibana", "config", ConfigurationSettings.DEFAULT_KIBANA_VERSION);
        givenUserIsNotClusterAdmin(userName);
        givenUserIsAdminForProjects("foo");


        //verify access to unique kibana index
        String docUri = String.format("%s/config/%s", kibanaIndex, ConfigurationSettings.DEFAULT_KIBANA_VERSION);
        whenGettingDocument(docUri);
        assertThatResponseIsSuccessful();
        assertThatDefaultIndexPatternIs("project.foo.uid.*");
    }

    @Test
    public void testUpgradeOfKibanaIndexForOps() throws Exception {
        final String userName = "adminuser";
        //artificially seed user's kibanaIndex
        String kibanaIndex = getKibanaIndex(KibanaIndexMode.UNIQUE, userName, false);
        XContentBuilder contentBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .field("defaultIndex",".all")
                .endObject();
        givenDocumentIsIndexed(kibanaIndex, "config", OLD_KIBANA_VERSION, contentBuilder);
        givenDocumentIsIndexed(kibanaIndex, KibanaUtils.INDICIES_TYPE, ".all", "bogus pattern");
        givenDocumentIsRemoved(".kibana", "config", ConfigurationSettings.DEFAULT_KIBANA_VERSION);
        givenUserIsClusterAdmin(userName);
        givenUserIsAdminForProjects("foo");
        
        
        //verify access to unique kibana index
        String docUri = String.format("%s/config/%s", kibanaIndex, ConfigurationSettings.DEFAULT_KIBANA_VERSION);
        whenGettingDocument(docUri);
        assertThatResponseIsSuccessful();
        assertThatDefaultIndexPatternIs(".all");
    }

    @SuppressWarnings("unchecked")
    private void assertThatDefaultIndexPatternIs(String indexPattern) throws Exception {
        Response response = (Response) testContext.get(RESPONSE);
        Map<String, Object> source = XContentHelper.convertToMap(JsonXContent.jsonXContent, response.body().byteStream(),false);
        Map<String, Object> doc =  (Map<String, Object>) source.get("_source");
        assertEquals("Exp. default index to be equal", indexPattern, doc.get("defaultIndex"));
    }
}
